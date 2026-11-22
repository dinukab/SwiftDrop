package com.swiftdrop.dispatch.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.swiftdrop.delivery.api.LegPlan;
import com.swiftdrop.delivery.api.LegType;

/**
 * Plans the legs of a parcel's journey from its origin hub, destination hub and the Main Hub (ADR 0007). A pure
 * function, so every route can be tested without a database.
 *
 * <ul>
 *   <li>same hub: pickup, final mile;</li>
 *   <li>different hubs: pickup, origin to Main Hub (unless the origin is the Main Hub), Main Hub to destination
 *       (unless the destination is the Main Hub), final mile.</li>
 * </ul>
 *
 * Pickup and final-mile legs start and end at the same hub: the origin hub for the pickup, the destination hub
 * for the final mile.
 */
public final class RoutePlanner {

    private RoutePlanner() {
    }

    public static List<LegPlan> plan(UUID originHubId, UUID destinationHubId, UUID mainHubId) {
        Objects.requireNonNull(originHubId, "originHubId");
        Objects.requireNonNull(destinationHubId, "destinationHubId");
        Objects.requireNonNull(mainHubId, "mainHubId");

        List<LegPlan> legs = new ArrayList<>();
        legs.add(new LegPlan(LegType.PICKUP, originHubId, originHubId));
        if (!originHubId.equals(destinationHubId)) {
            if (!originHubId.equals(mainHubId)) {
                legs.add(new LegPlan(LegType.INTER_HUB, originHubId, mainHubId));
            }
            if (!destinationHubId.equals(mainHubId)) {
                legs.add(new LegPlan(LegType.INTER_HUB, mainHubId, destinationHubId));
            }
        }
        legs.add(new LegPlan(LegType.FINAL_MILE, destinationHubId, destinationHubId));
        return List.copyOf(legs);
    }
}
