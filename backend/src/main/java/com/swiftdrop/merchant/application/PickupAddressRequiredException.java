package com.swiftdrop.merchant.application;

/**
 * An API order has no pickup and the merchant has no registered pickup address to use instead. The merchant adds one
 * with {@code PUT /api/v1/merchants/me/pickup-address}, or sends {@code pickup} with every order as before.
 */
public class PickupAddressRequiredException extends RuntimeException {

    public PickupAddressRequiredException() {
        super("pickup is required until the merchant registers a pickup address");
    }
}
