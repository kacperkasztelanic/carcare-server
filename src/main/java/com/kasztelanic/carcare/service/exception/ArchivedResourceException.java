package com.kasztelanic.carcare.service.exception;

public class ArchivedResourceException extends RuntimeException {

    public ArchivedResourceException() {
        super("Resource is archived");
    }
}
