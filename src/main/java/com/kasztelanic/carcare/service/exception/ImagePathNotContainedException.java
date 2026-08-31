package com.kasztelanic.carcare.service.exception;

/**
 * Raised by the image storage path helper when a file name resolves outside the configured data
 * directory. Deliberately has no {@code ExceptionTranslator} handler: every storage-service caller
 * catches it and falls back to its own failure sentinel, so it must never escape to the client.
 */
public class ImagePathNotContainedException extends RuntimeException {

    private final String name;

    public ImagePathNotContainedException(String name) {
        super("Image path escapes the data directory: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
