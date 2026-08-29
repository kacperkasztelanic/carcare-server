package com.kasztelanic.carcare.service.exception;

/**
 * Raised when an admin purge is attempted on a vehicle that is not archived. The purge interlock
 * (P3) requires archiving first, so disposing of a live vehicle is a deliberate two-step act.
 * Translated to a 409 by {@code ExceptionTranslator}.
 */
public class VehicleNotArchivedException extends RuntimeException {

    public VehicleNotArchivedException(Long id) {
        super("Vehicle " + id + " is not archived and cannot be purged");
    }
}
