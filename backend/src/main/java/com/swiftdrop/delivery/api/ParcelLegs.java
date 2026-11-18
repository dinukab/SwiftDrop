package com.swiftdrop.delivery.api;

import java.util.List;
import java.util.UUID;

/**
 * Shipment legs for {@code dispatch} (ADR 0007). {@code dispatch} plans the route and decides who is
 * eligible; {@code delivery} stores the legs and enforces their order and the parcel's state under the parcel
 * lock.
 */
public interface ParcelLegs {

    /**
     * Stores the planned legs of a parcel, numbered in the order given. The plan must start with
     * {@link LegType#PICKUP}, end with {@link LegType#FINAL_MILE} and have only {@link LegType#INTER_HUB} legs
     * in between.
     *
     * Idempotent: a parcel that already has legs (for example planned by a concurrent assignment) is left as it is.
     *
     * @return {@code true} if the legs were planned now, {@code false} if the parcel already had legs
     * @throws ParcelNotFoundException if the parcel does not exist
     * @throws ParcelConflictException if a parcel without legs is past {@code CREATED}, or the plan is malformed
     */
    boolean planLegs(UUID parcelId, List<LegPlan> plan);

    /** The parcel's legs in order; empty if none were planned. */
    List<LegView> findLegs(UUID parcelId);

    /**
     * Legs that wait for a courier at {@code sourceHubId}, oldest first: unassigned, not the pickup leg, the
     * previous leg complete and the parcel waiting at a hub.
     */
    PageResult<WaitingLeg> findWaitingLegs(UUID sourceHubId, PageRequest page);

    /**
     * Hands the parcel to a courier for a later leg. Allowed only for the next leg (not the pickup leg), after
     * the previous leg is complete, while the parcel waits at a hub ({@code HUB_ARRIVED} or {@code HUB_SORTED}).
     * Assigning another courier before the leg starts replaces the first. Not a status change.
     *
     * @throws ParcelNotFoundException if the parcel or the leg does not exist
     * @throws ParcelConflictException if the leg is not next, the previous leg is not complete, or the parcel is
     *         not waiting at a hub
     */
    LegView assignLegCourier(UUID parcelId, UUID legId, UUID courierUserId, UUID dispatcherUserId);
}
