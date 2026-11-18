package com.swiftdrop.dispatch.application;

/**
 * Thrown when a user cannot be bound to a hub: the account is disabled or lacks the
 * {@code DISPATCHER} role. The message names the rule only, never the user.
 */
public class DispatcherNotEligibleException extends RuntimeException {

    public DispatcherNotEligibleException(String message) {
        super(message);
    }
}
