package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Immutable, read-only parcel summary for cross-module queries (dispatch, merchant, tracking).
 *
 * <p>Supports both direct-customer and merchant-submitted parcels without forcing customer accounts
 * for external e-commerce buyers:
 * <ul>
 *   <li>For {@link ParcelSource#CUSTOMER}: {@code customerId} is non-null, {@code merchantId} and {@code merchantOrderReference} are null.</li>
 *   <li>For {@link ParcelSource#MERCHANT}: {@code merchantId} is non-null, {@code customerId} is null, and {@code merchantOrderReference} may be set.</li>
 *   <li>For unassigned parcels: {@code assignedCourierId} is null.</li>
 *   <li>{@code pickupProvinceCode} and {@code dropoffProvinceCode} are the stable codes of the pickup and dropoff
 *       zones (for example {@code LK-UP}), or {@code null} when the zone no longer exists. They name a province,
 *       not an address, so they are safe to show to dispatchers.</li>
 * </ul>
 */
public record ParcelSummary(
        UUID id,
        String trackingNumber,
        ParcelStatus status,
        ParcelSize size,
        int weightGrams,
        ParcelSource source,
        UUID customerId,
        UUID merchantId,
        String merchantOrderReference,
        UUID assignedCourierId,
        Instant createdAt,
        String pickupProvinceCode,
        String dropoffProvinceCode
) {
    public ParcelSummary {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** A summary without province codes, for callers that do not know them. */
    public ParcelSummary(UUID id, String trackingNumber, ParcelStatus status, ParcelSize size, int weightGrams,
            ParcelSource source, UUID customerId, UUID merchantId, String merchantOrderReference,
            UUID assignedCourierId, Instant createdAt) {
        this(id, trackingNumber, status, size, weightGrams, source, customerId, merchantId, merchantOrderReference,
                assignedCourierId, createdAt, null, null);
    }
}
