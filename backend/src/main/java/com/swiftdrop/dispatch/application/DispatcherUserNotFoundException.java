package com.swiftdrop.dispatch.application;

/** Thrown when a hub binding references a user ID not found in {@code identity}. */
public class DispatcherUserNotFoundException extends RuntimeException {

    public DispatcherUserNotFoundException() {
        super("User not found");
    }
}
