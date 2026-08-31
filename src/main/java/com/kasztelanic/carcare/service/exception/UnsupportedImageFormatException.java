package com.kasztelanic.carcare.service.exception;

/**
 * Raised on the vehicle image write path when the uploaded bytes are not a byte-verified PNG or
 * JPEG, or when a client declares a specific {@code image/*} type its bytes contradict. Translated
 * to a 400 by {@code ExceptionTranslator}; rolls the enclosing create/edit transaction back before
 * any row is written.
 */
public class UnsupportedImageFormatException extends RuntimeException {

    public UnsupportedImageFormatException(String detectedType) {
        super("Unsupported image format: " + detectedType + " — only PNG and JPEG are accepted");
    }

    public UnsupportedImageFormatException(String declaredType, String detectedType) {
        super("Declared image type " + declaredType + " does not match the uploaded content (" + detectedType + ")");
    }
}
