package com.swiftdrop.identity.api;

/**
 * Public signal that an email address is already registered. Other modules (for example merchant
 * onboarding) may catch this without depending on identity internals (ADR 0003).
 */
public class EmailTakenException extends RuntimeException {

    public EmailTakenException() {
        super("Email address is already registered");
    }

    public EmailTakenException(String message) {
        super(message);
    }
}
