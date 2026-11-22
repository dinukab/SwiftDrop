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
     * Final-mile legs that wait for a courier at {@code sourceHubId}, oldest first: unassigned, the previous leg
     * complete (received) and the parcel waiting at a hub. Hub-to-hub legs are not listed: they have no courier,
     * see {@link #findReadyTransfers}.
     */
    PageResult<WaitingLeg> findWaitingLegs(UUID sourceHubId, PageRequest page);

    /**
     * Hands the parcel to a courier for the final-mile leg. Allowed only for the next leg (not the pickup leg and not
     * a hub-to-hub leg), after the previous leg is complete, while the parcel waits at a hub ({@code HUB_ARRIVED} or
     * {@code HUB_SORTED}). Assigning another courier before the leg starts replaces the first. Not a status change.
     *
     * @throws ParcelNotFoundException if the parcel or the leg does not exist
     * @throws ParcelConflictException if the leg is a hub-to-hub leg, is not next, the previous leg is not complete,
     *         or the parcel is not waiting at a hub
     */
    LegView assignLegCourier(UUID parcelId, UUID legId, UUID courierUserId, UUID dispatcherUserId);

    /**
     * Hub-to-hub legs ready to be sent from {@code sourceHubId}, oldest first: the previous leg is complete (the
     * parcel was received at this hub), the parcel waits at a hub, and the leg was neither sent nor given a courier.
     */
    PageResult<WaitingLeg> findReadyTransfers(UUID sourceHubId, PageRequest page);

    /**
     * Hub-to-hub legs on their way to {@code destinationHubId}, oldest first: sent by the sending hub and not yet
     * confirmed received.
     */
    PageResult<WaitingLeg> findIncomingLegs(UUID destinationHubId, PageRequest page);

    /**
     * The sending hub sends the parcel straight to the next hub, with no courier: the parcel becomes
     * {@code IN_TRANSIT} and has no courier, and the previous courier loses access. Not a receipt: the parcel is not
     * at the destination until {@link #confirmLegReceipt}. Recorded in the status history with the dispatcher as
     * actor.
     *
     * @throws ParcelNotFoundException if the parcel or the leg does not exist
     * @throws ParcelConflictException if the leg is not a hub-to-hub leg, was already sent or has a courier, the
     *         previous leg is not complete, or the parcel is not waiting at a hub
     */
    LegView transferLeg(UUID parcelId, UUID legId, UUID dispatcherUserId);

    /**
     * The receiving hub confirms the parcel physically arrived: the leg ends, the parcel becomes
     * {@code HUB_ARRIVED} and may be given its next leg. Recorded in the status history with the dispatcher as actor.
     *
     * @throws ParcelNotFoundException if the parcel or the leg does not exist
     * @throws ParcelConflictException if the leg was not sent, was already received, or the parcel is not in transit
     */
    LegView confirmLegReceipt(UUID parcelId, UUID legId, UUID dispatcherUserId);
}
