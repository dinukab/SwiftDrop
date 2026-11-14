package com.swiftdrop.delivery.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Status lifecycle vocabulary of a parcel delivery (D-06, ADR 0003, ADR 0006).
 * Vocabulary only; state transition enforcement lives in the delivery domain.
 *
 * <p>{@link #PAUSED} and {@link #CANCELLED} are set only by the owning merchant before pickup;
 * couriers never move a parcel into or out of them.
 */
public enum ParcelStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED,
    /** Held by the merchant before pickup (for example while the order is still being prepared). */
    PAUSED,
    /** Cancelled by the merchant before pickup; final. */
    CANCELLED;

    private static final Set<ParcelStatus> FINAL =
            Collections.unmodifiableSet(EnumSet.of(DELIVERED, FAILED, CANCELLED));

    /** Whether nothing can change the parcel's status any more. */
    public boolean isFinal() {
        return FINAL.contains(this);
    }

    /** The final statuses, for queries that exclude finished parcels. */
    public static Set<ParcelStatus> finalStatuses() {
        return FINAL;
    }
}
