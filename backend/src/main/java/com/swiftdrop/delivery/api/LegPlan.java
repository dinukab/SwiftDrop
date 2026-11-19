package com.swiftdrop.delivery.api;

import java.util.Objects;
import java.util.UUID;

/**
 * One planned leg of a parcel's route, decided by {@code dispatch}, which knows the hubs. {@code delivery}
 * stores the hub IDs it is given.
 */
public record LegPlan(LegType legType, UUID sourceHubId, UUID destinationHubId) {

    public LegPlan {
        Objects.requireNonNull(legType, "legType must not be null");
        Objects.requireNonNull(sourceHubId, "sourceHubId must not be null");
        Objects.requireNonNull(destinationHubId, "destinationHubId must not be null");
    }
}
