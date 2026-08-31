package com.kasztelanic.carcare.service.exception;

/**
 * Raised when an image cannot be written to the configured data directory. Keeping this failure
 * unchecked ensures a vehicle create/edit transaction rolls back instead of persisting an empty
 * image sentinel after a partial or failed write.
 */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
