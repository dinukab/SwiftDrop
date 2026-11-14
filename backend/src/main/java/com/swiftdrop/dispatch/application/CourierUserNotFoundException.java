package com.swiftdrop.dispatch.application;

/**
 * Thrown when a courier profile operation references a user ID not found in {@code identity}.
 */
public class CourierUserNotFoundException extends RuntimeException {

    public CourierUserNotFoundException() {
        super("User not found");
    }
}

