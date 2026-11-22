package com.swiftdrop.identity.application;

/**
 * Thrown when an administrator attempts to delete their own account.
 */
public class SelfDeletionException extends RuntimeException {
    public SelfDeletionException(String message) {
        super(message);
    }
}
