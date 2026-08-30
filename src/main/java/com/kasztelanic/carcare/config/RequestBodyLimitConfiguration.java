package com.kasztelanic.carcare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.web.filter.RequestBodyLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.DispatcherType;
import java.util.EnumSet;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the request admission boundary before Spring Security's servlet filter. */
@Configuration
public class RequestBodyLimitConfiguration {

    @Bean
    public FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        FilterRegistrationBean<RequestBodyLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestBodyLimitFilter(objectMapper, meterRegistry));
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }
}
