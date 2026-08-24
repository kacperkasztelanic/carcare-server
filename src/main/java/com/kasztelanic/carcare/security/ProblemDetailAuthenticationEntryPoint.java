package com.kasztelanic.carcare.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kasztelanic.carcare.web.rest.errors.SecurityProblemDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the {@code application/problem+json} 401 body for requests that never reach the
 * {@code DispatcherServlet} (rejected in the Spring Security filter chain). See
 * {@code ExceptionTranslator.handleAuthentication} for the advice-level half of this pair.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
        throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problemDetail = SecurityProblemDetails.forSecurityError(HttpStatus.UNAUTHORIZED,
            authException.getMessage(), request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
