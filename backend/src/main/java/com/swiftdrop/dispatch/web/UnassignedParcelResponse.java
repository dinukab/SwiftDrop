package com.swiftdrop.dispatch.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelSummary;

/** A parcel waiting for a courier. No addresses or phone numbers: dispatch does not need them. */
record UnassignedParcelResponse(
        UUID id,
        String trackingNumber,
        String size,
        int weightGrams,
        String source,
        String merchantOrderReference,
        Instant createdAt) {

    static UnassignedParcelResponse from(ParcelSummary parcel) {
        return new UnassignedParcelResponse(
                parcel.id(),
                parcel.trackingNumber(),
                parcel.size().name(),
                parcel.weightGrams(),
                parcel.source().name(),
                parcel.merchantOrderReference(),
                parcel.createdAt());
    }
}
