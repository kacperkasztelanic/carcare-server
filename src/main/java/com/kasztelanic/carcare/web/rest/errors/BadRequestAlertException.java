package com.kasztelanic.carcare.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.net.URI;

public class BadRequestAlertException extends ErrorResponseException {

    private final String entityName;

    private final String errorKey;

    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey);
    }

    public BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey) {
        super(HttpStatus.BAD_REQUEST, problemDetail(type, defaultMessage, entityName, errorKey), null);
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getErrorKey() {
        return errorKey;
    }

    private static ProblemDetail problemDetail(URI type, String defaultMessage, String entityName, String errorKey) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setType(type);
        problemDetail.setTitle(defaultMessage);
        problemDetail.setProperty("message", "error." + errorKey);
        problemDetail.setProperty("params", entityName);
        return problemDetail;
    }
}
