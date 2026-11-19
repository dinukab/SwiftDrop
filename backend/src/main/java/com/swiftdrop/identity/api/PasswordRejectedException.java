package com.swiftdrop.identity.api;

/**
 * Public signal that a password was rejected by identity rules. Other modules may catch this
 * without depending on identity internals (ADR 0003).
 */
public class PasswordRejectedException extends RuntimeException {

    public PasswordRejectedException(String message) {
        super(message);
    }
}
