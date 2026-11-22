package com.swiftdrop.delivery.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of one shipment leg. {@code assignedCourierId} is a courier user ID; it and
 * {@code assignedAt} are null until a courier is assigned, and {@code completedAt} is null until the leg ends.
 *
 * <p>A hub-to-hub leg has no courier: {@code transferredAt/By} say when and by which dispatcher the sending hub
 * sent the parcel, and {@code receivedAt/By} when and by which dispatcher the receiving hub confirmed it. They are
 * null until then, and always null for other legs.
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
        Instant completedAt,
        Instant transferredAt,
        UUID transferredBy,
        Instant receivedAt,
        UUID receivedBy) {

    /** A leg that was not transferred by a dispatcher (pickup, final mile, or a courier-driven leg). */
    public LegView(UUID id, UUID parcelId, int sequence, LegType legType, UUID sourceHubId, UUID destinationHubId,
            UUID assignedCourierId, Instant assignedAt, Instant completedAt) {
        this(id, parcelId, sequence, legType, sourceHubId, destinationHubId, assignedCourierId, assignedAt,
                completedAt, null, null, null, null);
    }
}
