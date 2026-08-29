package com.kasztelanic.carcare.service.exception;

/**
 * Raised when an attempt is made to delete a protected account ({@code system} or
 * {@code anonymoususer}). Translated to a 400 by {@code ExceptionTranslator}.
 */
public class ProtectedLoginException extends RuntimeException {

    public ProtectedLoginException(String login) {
        super("User '" + login + "' is protected and cannot be deleted");
    }
}
