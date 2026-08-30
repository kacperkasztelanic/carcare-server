package com.kasztelanic.carcare.web.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.web.rest.errors.ErrorConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestBodyLimitFilterTest {

    private static final String REQUEST_URI = "/api/vehicle";
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60);

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @BeforeEach
    @AfterEach
    void resetWarningGate() {
        AtomicLong warningGate = (AtomicLong) org.springframework.test.util.ReflectionTestUtils
            .getField(RequestBodyLimitFilter.class, "LAST_WARNING_NANOS");
        warningGate.set(Long.MIN_VALUE);
    }

    @Test
    void acceptsDeclaredLengthsThroughTheInclusiveLimit() throws Exception {
        long[] acceptedLengths = { 0L, 1L, RequestBodyLimitFilter.MAX_REQUEST_BODY_BYTES };

        for (long contentLength : acceptedLengths) {
            RequestBodyLimitFilter filter = newFilter(new SimpleMeterRegistry(), () -> 0L);
            HttpServletRequest request = requestWithMetadata(contentLength, "HTTP/1.1", null, REQUEST_URI);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = Mockito.mock(FilterChain.class);

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Test
    void rejectsDeclaredLengthJustAboveTheLimitBeforeBodyAccess() throws Exception {
        HttpServletRequest request = requestWithMetadata(
            RequestBodyLimitFilter.MAX_REQUEST_BODY_BYTES + 1,
            "HTTP/1.1",
            null,
            REQUEST_URI
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        newFilter(new SimpleMeterRegistry(), () -> 0L).doFilter(request, response, filterChain);

        assertPayloadTooLarge(response, REQUEST_URI);
        verify(filterChain, never()).doFilter(any(), any());
        verify(request, never()).getInputStream();
        verify(request, never()).getReader();
    }

    @Test
    void rejectsHttp11TransferCodedUnknownLengthBeforeBodyAccess() throws Exception {
        HttpServletRequest request = requestWithMetadata(-1L, "HTTP/1.1", "chunked", "/api/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        newFilter(new SimpleMeterRegistry(), () -> 0L).doFilter(request, response, filterChain);

        assertPayloadTooLarge(response, "/api/upload");
        verify(filterChain, never()).doFilter(any(), any());
        verify(request, never()).getInputStream();
        verify(request, never()).getReader();
    }

    @Test
    void passesUnframedUnknownLengthWithoutReadingTheBody() throws Exception {
        HttpServletRequest request = requestWithMetadata(-1L, "HTTP/1.1", null, "/api/bodyless");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        newFilter(new SimpleMeterRegistry(), () -> 0L).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void passesHttp2TransferEncodingTrailerMetadata() throws Exception {
        HttpServletRequest request = requestWithMetadata(-1L, "HTTP/2", "trailers", "/api/http2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        newFilter(new SimpleMeterRegistry(), () -> 0L).doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void preservesAcceptedInputStreamBytes() throws Exception {
        byte[] content = "wire bytes stay unchanged".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = bodyRequest(content);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        newFilter(new SimpleMeterRegistry(), () -> 0L).doFilter(request, response, filterChain);

        assertThat(request.getContentLengthLong()).isEqualTo(content.length);
        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(((HttpServletRequest) filterChain.getRequest()).getInputStream().readAllBytes())
            .containsExactly(content);
    }

    @Test
    void preservesAcceptedUtf8ReaderContents() throws Exception {
        String content = "Zażółć gęślą jaźń €";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = bodyRequest(bytes);
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        newFilter(new SimpleMeterRegistry(), () -> 0L).doFilter(request, response, filterChain);

        assertThat(bytes.length).isGreaterThan(content.length());
        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(((HttpServletRequest) filterChain.getRequest()).getReader().readLine()).isEqualTo(content);
    }

    @Test
    void exposesFixedRejectionMetersAndCountsEveryRejection() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RequestBodyLimitFilter filter = newFilter(meterRegistry, () -> 0L);

        reject(filter, requestWithMetadata(RequestBodyLimitFilter.MAX_REQUEST_BODY_BYTES + 1, "HTTP/1.1", null, "/one"));
        reject(filter, requestWithMetadata(RequestBodyLimitFilter.MAX_REQUEST_BODY_BYTES + 1, "HTTP/1.1", null, "/two"));
        reject(filter, requestWithMetadata(-1L, "HTTP/1.1", "chunked", "/three"));

        Counter declaredTooLarge = meterRegistry.find(RequestBodyLimitFilter.REJECTIONS_METER_NAME)
            .tag(RequestBodyLimitFilter.REASON_TAG, RequestBodyLimitFilter.DECLARED_TOO_LARGE_REASON)
            .counter();
        Counter unknownLength = meterRegistry.find(RequestBodyLimitFilter.REJECTIONS_METER_NAME)
            .tag(RequestBodyLimitFilter.REASON_TAG, RequestBodyLimitFilter.UNKNOWN_LENGTH_REASON)
            .counter();

        assertThat(declaredTooLarge).isNotNull();
        assertThat(unknownLength).isNotNull();
        assertThat(declaredTooLarge.count()).isEqualTo(2);
        assertThat(unknownLength.count()).isEqualTo(1);
        assertMeterContract(declaredTooLarge, RequestBodyLimitFilter.DECLARED_TOO_LARGE_REASON);
        assertMeterContract(unknownLength, RequestBodyLimitFilter.UNKNOWN_LENGTH_REASON);
        assertThat(meterRegistry.getMeters()).hasSize(2);
    }

    @Test
    void throttlesWarningsGloballyAndReenablesAfterSixtySeconds() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000L);
        RequestBodyLimitFilter firstFilter = newFilter(new SimpleMeterRegistry(), now::get);
        RequestBodyLimitFilter secondFilter = newFilter(new SimpleMeterRegistry(), now::get);
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        try {
            reject(firstFilter, oversizedRequest("/first"));
            reject(secondFilter, oversizedRequest("/suppressed"));
            now.addAndGet(WARNING_INTERVAL_NANOS - 1);
            reject(firstFilter, oversizedRequest("/still-suppressed"));
            now.incrementAndGet();
            reject(secondFilter, oversizedRequest("/reenabled"));

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                    "Rejected request body: path=/first, declaredLength=4194305",
                    "Rejected request body: path=/reenabled, declaredLength=4194305"
                );
        } finally {
            detachLogAppender(appender);
        }
    }

    @Test
    void suppressesConcurrentWarningsAcrossFilterInstances() throws Exception {
        AtomicLong now = new AtomicLong(2_000_000L);
        RequestBodyLimitFilter firstFilter = newFilter(new SimpleMeterRegistry(), now::get);
        RequestBodyLimitFilter secondFilter = newFilter(new SimpleMeterRegistry(), now::get);
        ListAppender<ILoggingEvent> appender = attachLogAppender();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 8; index++) {
                RequestBodyLimitFilter filter = index % 2 == 0 ? firstFilter : secondFilter;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    reject(filter, oversizedRequest("/concurrent"));
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            assertThat(appender.list).hasSize(1);

            now.addAndGet(WARNING_INTERVAL_NANOS - 1);
            reject(firstFilter, oversizedRequest("/before-interval"));
            assertThat(appender.list).hasSize(1);

            now.incrementAndGet();
            reject(secondFilter, oversizedRequest("/after-interval"));
            assertThat(appender.list).hasSize(2);
        } finally {
            executor.shutdownNow();
            detachLogAppender(appender);
        }
    }

    private RequestBodyLimitFilter newFilter(SimpleMeterRegistry meterRegistry, LongSupplier nanoTimeSupplier) {
        return new RequestBodyLimitFilter(objectMapper, meterRegistry, nanoTimeSupplier);
    }

    private HttpServletRequest oversizedRequest(String requestUri) {
        return requestWithMetadata(
            RequestBodyLimitFilter.MAX_REQUEST_BODY_BYTES + 1,
            "HTTP/1.1",
            null,
            requestUri
        );
    }

    private HttpServletRequest requestWithMetadata(long contentLength, String protocol, String transferEncoding, String requestUri) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(contentLength);
        when(request.getProtocol()).thenReturn(protocol);
        when(request.getHeader(RequestBodyLimitFilter.TRANSFER_ENCODING_HEADER)).thenReturn(transferEncoding);
        when(request.getRequestURI()).thenReturn(requestUri);
        when(request.getDispatcherType()).thenReturn(DispatcherType.REQUEST);
        return request;
    }

    private MockHttpServletRequest bodyRequest(byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(REQUEST_URI);
        request.setProtocol("HTTP/1.1");
        request.setContent(content);
        return request;
    }

    private void reject(RequestBodyLimitFilter filter, HttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), Mockito.mock(FilterChain.class));
    }

    private void assertPayloadTooLarge(MockHttpServletResponse response, String requestUri) throws Exception {
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        MediaType responseType = MediaType.parseMediaType(response.getContentType());
        assertThat(responseType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(responseType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());

        JsonNode payload = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(payload.get("type").asText()).isEqualTo(ErrorConstants.DEFAULT_TYPE.toString());
        assertThat(payload.get("title").asText()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase());
        assertThat(payload.get("status").asInt()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(payload.get("detail").asText()).isEqualTo(RequestBodyLimitFilter.REQUEST_BODY_TOO_LARGE_DETAIL);
        assertThat(payload.get("message").asText()).isEqualTo("error.http.413");
        assertThat(payload.get("path").asText()).isEqualTo(requestUri);
    }

    private void assertMeterContract(Counter counter, String reason) {
        List<Tag> tags = counter.getId().getTags();
        assertThat(counter.getId().getName()).isEqualTo(RequestBodyLimitFilter.REJECTIONS_METER_NAME);
        assertThat(counter.getId().getDescription()).isEqualTo(RequestBodyLimitFilter.REJECTIONS_METER_DESCRIPTION);
        assertThat(counter.getId().getBaseUnit()).isEqualTo(RequestBodyLimitFilter.REJECTIONS_METER_BASE_UNIT);
        assertThat(tags).extracting(Tag::getKey).containsExactly(RequestBodyLimitFilter.REASON_TAG);
        assertThat(tags).extracting(Tag::getValue).containsExactly(reason);
    }

    private ListAppender<ILoggingEvent> attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestBodyLimitFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestBodyLimitFilter.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
