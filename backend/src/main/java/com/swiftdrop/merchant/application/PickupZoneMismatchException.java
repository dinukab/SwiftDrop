package com.swiftdrop.merchant.application;

/**
 * An API order names a pickup in another province than the merchant's registered pickup address. Refused so a wrong
 * zone cannot send the order to another hub's dispatcher and the courier to the wrong place.
 */
public class PickupZoneMismatchException extends RuntimeException {

    public PickupZoneMismatchException() {
        super("The pickup must be in the province of the merchant's registered pickup address; "
                + "omit pickup to use the registered address");
    }
}
