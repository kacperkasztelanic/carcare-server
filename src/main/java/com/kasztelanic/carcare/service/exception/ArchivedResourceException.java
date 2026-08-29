package com.kasztelanic.carcare.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.GONE, reason = "Resource is archived")
public class ArchivedResourceException extends RuntimeException {

    public ArchivedResourceException() {
        super("Resource is archived");
    }
}
