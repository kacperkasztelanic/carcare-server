package com.kasztelanic.carcare.service.exception;

public class InvalidLookupTypeException extends RuntimeException {

    public InvalidLookupTypeException(String lookupName) {
        super("Missing or unknown lookup: " + lookupName);
    }
}
