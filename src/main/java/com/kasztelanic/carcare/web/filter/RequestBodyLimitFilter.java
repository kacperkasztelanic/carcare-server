package com.kasztelanic.carcare.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.web.rest.errors.SecurityProblemDetails;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects request bodies that cannot be admitted without buffering them in the application.
 *
 * <p>The filter deliberately checks only servlet metadata. It does not wrap or read accepted
 * requests, and it rejects an unknown length only when HTTP/1.1 explicitly advertises transfer
 * coding. This keeps the admission decision before Spring Security and MVC.</p>
 */
@Slf4j
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    public static final long MAX_REQUEST_BODY_BYTES = 4_194_304L;
    public static final String REJECTIONS_METER_NAME = "http.server.request-body.rejections";
    public static final String REJECTIONS_METER_DESCRIPTION = "Rejected HTTP request bodies";
    public static final String REJECTIONS_METER_BASE_UNIT = "requests";
    public static final String REASON_TAG = "reason";
    public static final String DECLARED_TOO_LARGE_REASON = "declared-too-large";
    public static final String UNKNOWN_LENGTH_REASON = "unknown-length";
    public static final String TRANSFER_ENCODING_HEADER = "Transfer-Encoding";
    public static final String HTTP_1_1_PROTOCOL = "HTTP/1.1";
    public static final String REQUEST_BODY_TOO_LARGE_DETAIL = "Request body exceeds the maximum permitted size.";

    private static final long WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final AtomicLong LAST_WARNING_NANOS = new AtomicLong(Long.MIN_VALUE);

    private final ObjectMapper objectMapper;
    private final Counter declaredTooLargeCounter;
    private final Counter unknownLengthCounter;
    private final LongSupplier nanoTimeSupplier;

    public RequestBodyLimitFilter(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this(objectMapper, meterRegistry, System::nanoTime);
    }

    public RequestBodyLimitFilter(ObjectMapper objectMapper, MeterRegistry meterRegistry, LongSupplier nanoTimeSupplier) {
        this.objectMapper = objectMapper;
        this.declaredTooLargeCounter = rejectionCounter(meterRegistry, DECLARED_TOO_LARGE_REASON);
        this.unknownLengthCounter = rejectionCounter(meterRegistry, UNKNOWN_LENGTH_REASON);
        this.nanoTimeSupplier = nanoTimeSupplier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        String rejectionReason = rejectionReason(request, contentLength);
        if (rejectionReason != null) {
            incrementCounter(rejectionReason);
            warnIfAllowed(request.getRequestURI(), contentLength);
            writePayloadTooLarge(response, request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Counter rejectionCounter(MeterRegistry meterRegistry, String reason) {
        return Counter.builder(REJECTIONS_METER_NAME)
            .description(REJECTIONS_METER_DESCRIPTION)
            .baseUnit(REJECTIONS_METER_BASE_UNIT)
            .tag(REASON_TAG, reason)
            .register(meterRegistry);
    }

    private String rejectionReason(HttpServletRequest request, long contentLength) {
        if (contentLength > MAX_REQUEST_BODY_BYTES) {
            return DECLARED_TOO_LARGE_REASON;
        }
        if (contentLength == -1L && HTTP_1_1_PROTOCOL.equals(request.getProtocol())
            && request.getHeader(TRANSFER_ENCODING_HEADER) != null) {
            return UNKNOWN_LENGTH_REASON;
        }
        return null;
    }

    private void incrementCounter(String reason) {
        if (DECLARED_TOO_LARGE_REASON.equals(reason)) {
            declaredTooLargeCounter.increment();
        } else {
            unknownLengthCounter.increment();
        }
    }

    private void warnIfAllowed(String path, long contentLength) {
        long now = nanoTimeSupplier.getAsLong();
        while (true) {
            long last = LAST_WARNING_NANOS.get();
            if (last != Long.MIN_VALUE && now - last < WARNING_INTERVAL_NANOS) {
                return;
            }
            if (LAST_WARNING_NANOS.compareAndSet(last, now)) {
                log.warn("Rejected request body: path={}, declaredLength={}", path, contentLength);
                return;
            }
        }
    }

    private void writePayloadTooLarge(HttpServletResponse response, String requestUri) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        var problemDetail = SecurityProblemDetails.forSecurityError(
            HttpStatus.PAYLOAD_TOO_LARGE,
            REQUEST_BODY_TOO_LARGE_DETAIL,
            requestUri
        );
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
