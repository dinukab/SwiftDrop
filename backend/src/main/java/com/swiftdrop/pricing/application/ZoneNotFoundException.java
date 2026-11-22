package com.swiftdrop.pricing.application;

import java.util.UUID;

/** No zone has the requested id (HTTP 404). */
public class ZoneNotFoundException extends RuntimeException {

    public ZoneNotFoundException(UUID id) {
        super("Zone " + id + " not found");
    }
}
