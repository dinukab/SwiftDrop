package com.swiftdrop.dispatch.application;

/**
 * Thrown when a courier profile ID does not exist.
 */
public class CourierProfileNotFoundException extends RuntimeException {

    public CourierProfileNotFoundException() {
        super("Courier profile not found");
    }
}
