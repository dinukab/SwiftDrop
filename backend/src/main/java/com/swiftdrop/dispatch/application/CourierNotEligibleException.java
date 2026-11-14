package com.swiftdrop.dispatch.application;

/**
 * Thrown when a courier cannot receive assignments. The message names the broken rule only, never
 * personal data.
 */
public class CourierNotEligibleException extends RuntimeException {

    public CourierNotEligibleException(String message) {
        super(message);
    }
}
