package com.swiftdrop.dispatch.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelSummary;

/**
 * An unassigned parcel for the dispatcher's queue. Holds no recipient or sender personal data: the
 * dispatcher sees the tracking number, size and the store's order reference only.
 */
public record UnassignedParcelResponse(
        UUID id,
        String trackingNumber,
        String status,
        String source,
        String size,
        int weightGrams,
        String merchantOrderReference,
        Instant createdAt
) {
    public static UnassignedParcelResponse from(ParcelSummary summary) {
        return new UnassignedParcelResponse(
                summary.id(),
                summary.trackingNumber(),
                summary.status().name(),
                summary.source().name(),
                summary.size().name(),
                summary.weightGrams(),
                summary.merchantOrderReference(),
                summary.createdAt()
        );
    }
}
