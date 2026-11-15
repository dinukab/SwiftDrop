package com.swiftdrop.dispatch.application;

/**
 * The user cannot be assigned deliveries: disabled, not a courier, or without an active courier
 * profile (contract §5.4). The message names the rule only, never the user.
 */
public class CourierNotEligibleException extends RuntimeException {

    public CourierNotEligibleException(String message) {
        super(message);
    }
}
