package com.swiftdrop.pricing.application;

/** A zone code or name is already used by another zone (HTTP 409). */
public class ZoneConflictException extends RuntimeException {

    public ZoneConflictException(String message) {
        super(message);
    }
}
