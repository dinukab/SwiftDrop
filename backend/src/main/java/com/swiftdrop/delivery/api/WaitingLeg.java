package com.swiftdrop.delivery.api;

import com.swiftdrop.pricing.api.ParcelSize;

/**
 * A leg that waits for a courier at a hub: the previous leg is complete and the parcel is at the hub. No
 * addresses or contacts, only what a dispatcher needs to pick a courier.
 */
public record WaitingLeg(LegView leg, String trackingNumber, ParcelSize size, int weightGrams) {
}
