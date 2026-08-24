package com.kasztelanic.carcare.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class EmailNotFoundException extends ErrorResponseException {

    public EmailNotFoundException() {
        super(HttpStatus.BAD_REQUEST, problemDetail(), null);
    }

    private static ProblemDetail problemDetail() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setType(ErrorConstants.EMAIL_NOT_FOUND_TYPE);
        problemDetail.setTitle("Email address not registered");
        return problemDetail;
    }
}
