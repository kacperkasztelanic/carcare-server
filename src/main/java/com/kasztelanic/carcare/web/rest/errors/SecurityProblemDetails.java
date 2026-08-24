package com.kasztelanic.carcare.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the {@link ProblemDetail} body for 401/403 responses. Used identically by
 * {@link ExceptionTranslator}'s advice-level security handlers and by the filter-chain
 * {@code AuthenticationEntryPoint} / {@code AccessDeniedHandler} beans in {@code config}, so
 * the two layers Spring Security exercises for these statuses cannot drift apart. Public
 * rather than package-private because those filter-chain beans live in a different package.
 */
public final class SecurityProblemDetails {

    private SecurityProblemDetails() {
    }

    public static ProblemDetail forSecurityError(HttpStatus status, String detail, String path) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setType(ErrorConstants.DEFAULT_TYPE);
        problemDetail.setDetail(detail);
        problemDetail.setProperty("message", "error.http." + status.value());
        problemDetail.setProperty("path", path);
        return problemDetail;
    }
}
