package com.kasztelanic.carcare.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class InvalidPasswordException extends ErrorResponseException {

    public InvalidPasswordException() {
        super(HttpStatus.BAD_REQUEST, problemDetail(), null);
    }

    private static ProblemDetail problemDetail() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setType(ErrorConstants.INVALID_PASSWORD_TYPE);
        problemDetail.setTitle("Incorrect password");
        return problemDetail;
    }
}
