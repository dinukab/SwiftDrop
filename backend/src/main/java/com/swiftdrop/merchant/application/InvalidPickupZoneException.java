package com.swiftdrop.merchant.application;

/** The pickup address names a zone (province) that does not exist; shown to the caller as a field error. */
public class InvalidPickupZoneException extends RuntimeException {

    public InvalidPickupZoneException() {
        super("Unknown pickup zone");
    }
}
