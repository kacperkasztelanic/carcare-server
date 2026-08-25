package com.kasztelanic.carcare.web.rest.errors;

import com.kasztelanic.carcare.service.exception.UsernameAlreadyUsedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import com.kasztelanic.carcare.web.rest.util.HeaderUtil;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures.
 * The error response follows RFC7807 - Problem Details for HTTP APIs (https://tools.ietf.org/html/rfc7807).
 */
@Slf4j
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

    private static final String MESSAGE_KEY = ErrorConstants.MESSAGE_KEY;
    private static final String PATH_KEY = ErrorConstants.PATH_KEY;
    private static final URI BLANK_TYPE = URI.create("about:blank");

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Post-processes every {@link ProblemDetail} body — built here or by the superclass's
     * sixteen built-in handlers — to add {@code path}, the {@code message} fallback, and the
     * {@code about:blank} -> {@link ErrorConstants#DEFAULT_TYPE} substitution. This is the
     * single hook all of this class's custom handlers route through, so the behaviour cannot
     * drift between them.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                               HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            if (BLANK_TYPE.equals(problemDetail.getType())) {
                problemDetail.setType(ErrorConstants.DEFAULT_TYPE);
            }
            problemDetail.setProperty(PATH_KEY, requestUri(request));
            if (problemDetail.getProperties() == null || !problemDetail.getProperties().containsKey(MESSAGE_KEY)) {
                problemDetail.setProperty(MESSAGE_KEY, "error.http." + statusCode.value());
            }
        }
        return response;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        BindingResult result = ex.getBindingResult();
        List<FieldErrorVm> fieldErrors = result.getFieldErrors().stream()
            .map(f -> new FieldErrorVm(f.getObjectName().replaceFirst("DTO$", ""), f.getField(), f.getCode()))
            .collect(Collectors.toList());

        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        // Set explicitly: without it the type would fall through to DEFAULT_TYPE in
        // handleExceptionInternal, changing the URI the client sees for every validation failure.
        problemDetail.setType(ErrorConstants.CONSTRAINT_VIOLATION_TYPE);
        problemDetail.setTitle("Method argument not valid");
        problemDetail.setProperty(MESSAGE_KEY, ErrorConstants.ERR_VALIDATION);
        problemDetail.setProperty("fieldErrors", fieldErrors);
        return handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNoSuchElementException(NoSuchElementException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setProperty(MESSAGE_KEY, ErrorConstants.ENTITY_NOT_FOUND_TYPE);
        return handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(com.kasztelanic.carcare.service.exception.EmailAlreadyUsedException.class)
    public ResponseEntity<Object> handleEmailAlreadyUsedException(
        com.kasztelanic.carcare.service.exception.EmailAlreadyUsedException ex, WebRequest request) {
        EmailAlreadyUsedException problem = new EmailAlreadyUsedException();
        HttpHeaders headers = HeaderUtil.createFailureAlert(applicationName, true, problem.getEntityName(),
            problem.getErrorKey(), problem.getBody().getTitle());
        return handleExceptionInternal(ex, problem.getBody(), headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(UsernameAlreadyUsedException.class)
    public ResponseEntity<Object> handleUsernameAlreadyUsedException(UsernameAlreadyUsedException ex, WebRequest request) {
        LoginAlreadyUsedException problem = new LoginAlreadyUsedException();
        HttpHeaders headers = HeaderUtil.createFailureAlert(applicationName, true, problem.getEntityName(),
            problem.getErrorKey(), problem.getBody().getTitle());
        return handleExceptionInternal(ex, problem.getBody(), headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(com.kasztelanic.carcare.service.exception.InvalidPasswordException.class)
    public ResponseEntity<Object> handleInvalidPasswordException(
        com.kasztelanic.carcare.service.exception.InvalidPasswordException ex, WebRequest request) {
        InvalidPasswordException problem = new InvalidPasswordException();
        return handleExceptionInternal(ex, problem.getBody(), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(BadRequestAlertException.class)
    public ResponseEntity<Object> handleBadRequestAlertException(BadRequestAlertException ex, WebRequest request) {
        HttpHeaders headers = HeaderUtil.createFailureAlert(applicationName, true, ex.getEntityName(), ex.getErrorKey(),
            ex.getBody().getTitle());
        return handleExceptionInternal(ex, ex.getBody(), headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Object> handleConcurrencyFailure(ConcurrencyFailureException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setProperty(MESSAGE_KEY, ErrorConstants.ERR_CONCURRENCY_FAILURE);
        return handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    /**
     * Advice-level half of the 401/403 handling; {@code security.ProblemDetailAccessDeniedHandler}
     * is the filter-chain half, since {@link org.springframework.security.web.access.AccessDeniedHandler}
     * runs before the {@code DispatcherServlet} and never reaches this advice. Both delegate to
     * {@link SecurityProblemDetails} so the bodies cannot drift apart.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problemDetail = SecurityProblemDetails.forSecurityError(HttpStatus.FORBIDDEN, ex.getMessage(),
            requestUri(request));
        return handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    /**
     * Advice-level half of 401 handling; {@code security.ProblemDetailAuthenticationEntryPoint}
     * is the filter-chain half. See {@link #handleAccessDenied}.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        ProblemDetail problemDetail = SecurityProblemDetails.forSecurityError(HttpStatus.UNAUTHORIZED, ex.getMessage(),
            requestUri(request));
        return handleExceptionInternal(ex, problemDetail, new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }

    /**
     * Catch-all for anything not handled above or by the superclass's sixteen built-in
     * handlers. Registered for {@link Exception} rather than {@link Throwable} so it can route
     * through {@link #handleExceptionInternal}, which requires an {@code Exception}; bare
     * {@link Error}s are not caught, matching how the rest of the stack already treats them.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUncaught(Exception ex, WebRequest request) {
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        HttpStatus status = responseStatus != null ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR;
        // A deliberate 4xx carries no diagnostic value at error level; reserve that for genuine faults.
        if (status.is5xxServerError()) {
            log.error("Unhandled exception", ex);
        } else {
            log.warn("Unhandled exception mapped to {}", status, ex);
        }
        String title = (responseStatus != null && StringUtils.hasText(responseStatus.reason()))
            ? responseStatus.reason()
            : "Internal Server Error";
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        return handleExceptionInternal(ex, problemDetail, new HttpHeaders(), status, request);
    }

    private static String requestUri(WebRequest request) {
        return request instanceof ServletWebRequest servletWebRequest
            ? servletWebRequest.getRequest().getRequestURI()
            : "";
    }
}
