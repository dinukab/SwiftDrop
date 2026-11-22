package com.swiftdrop.dispatch.application;

/**
 * Thrown when a dispatcher acts before an admin has bound them to a hub. Hub-scoped operations cannot
 * tell which parcels are theirs, so they are refused rather than widened to every hub.
 */
public class DispatcherHubNotAssignedException extends RuntimeException {

    public DispatcherHubNotAssignedException() {
        super("Dispatcher has no hub assigned");
    }
}
