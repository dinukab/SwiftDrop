package com.swiftdrop.dispatch.application;

/**
 * Thrown when a user exists in {@code identity} but cannot own a courier profile (disabled account
 * or missing {@code COURIER} role).
 */
public class CourierUserNotEligibleException extends RuntimeException {

    public CourierUserNotEligibleException(String message) {
        super(message);
    }
}

