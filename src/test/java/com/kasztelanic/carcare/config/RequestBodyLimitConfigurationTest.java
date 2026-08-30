package com.kasztelanic.carcare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.web.filter.RequestBodyLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RequestBodyLimitConfigurationTest {

    @Test
    void exposesExactlyOnePreSecurityRegistrationAndNoStandaloneFilterBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            Map<String, FilterRegistrationBean> registrations = context.getBeansOfType(FilterRegistrationBean.class);

            assertThat(registrations).hasSize(1);
            assertThat(context.getBeansOfType(RequestBodyLimitFilter.class)).isEmpty();

            FilterRegistrationBean<?> registration = registrations.values().iterator().next();
            assertThat(registration.getFilter()).isInstanceOf(RequestBodyLimitFilter.class);
            assertThat(registration.getUrlPatterns()).containsExactly("/*");
            assertThat(registration.getOrder()).isLessThan(SecurityProperties.DEFAULT_FILTER_ORDER);
        }
    }

    @Test
    void registersOneRequestMappingAgainstTheServletContext() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            FilterRegistrationBean<?> registration = context.getBeansOfType(FilterRegistrationBean.class)
                .values()
                .iterator()
                .next();
            MockServletContext servletContext = spy(new MockServletContext());
            FilterRegistration.Dynamic dynamicRegistration = mock(FilterRegistration.Dynamic.class);
            doReturn(dynamicRegistration)
                .when(servletContext)
                .addFilter(anyString(), same(registration.getFilter()));

            registration.onStartup(servletContext);

            verify(servletContext, times(1)).addFilter(anyString(), same(registration.getFilter()));
            verify(dynamicRegistration, times(1))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RequestBodyLimitConfiguration.class)
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
