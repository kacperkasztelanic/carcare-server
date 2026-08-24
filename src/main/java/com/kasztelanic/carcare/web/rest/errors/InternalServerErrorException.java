package com.kasztelanic.carcare.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Simple exception with a message, that returns an Internal Server Error code.
 */
public class InternalServerErrorException extends ErrorResponseException {

    public InternalServerErrorException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, problemDetail(message), null);
    }

    private static ProblemDetail problemDetail(String message) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setType(ErrorConstants.DEFAULT_TYPE);
        problemDetail.setTitle(message);
        return problemDetail;
    }
}
