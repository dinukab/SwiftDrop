package com.swiftdrop.dispatch.application;

/**
 * Thrown when attempting to create a second courier profile for the same {@code userId}.
 */
public class DuplicateCourierProfileException extends RuntimeException {

    public DuplicateCourierProfileException() {
        super("A courier profile already exists for this user");
    }
}

