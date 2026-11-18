package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of one shipment leg. {@code assignedCourierId} is a courier user ID; it and
 * {@code assignedAt} are null until a courier is assigned, and {@code completedAt} is null until the leg ends.
 */
public record LegView(
        UUID id,
        UUID parcelId,
        int sequence,
        LegType legType,
        UUID sourceHubId,
        UUID destinationHubId,
        UUID assignedCourierId,
        Instant assignedAt,
        Instant completedAt) {
}
