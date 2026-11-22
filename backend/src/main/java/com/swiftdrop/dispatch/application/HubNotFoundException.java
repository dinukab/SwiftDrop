package com.swiftdrop.dispatch.application;

/** Thrown when a hub code is unknown or the hub is inactive. */
public class HubNotFoundException extends RuntimeException {

    public HubNotFoundException() {
        super("Hub not found");
    }
}
