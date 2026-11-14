package com.swiftdrop.delivery.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * The parcel status transitions of ADR 0006, kept in two tables so that no actor can use the other
 * actor's moves.
 *
 * <ul>
 *   <li><b>Courier moves</b> (§1): forward one step at a time, {@code FAILED} from {@code PICKED_UP}
 *       through {@code OUT_FOR_DELIVERY}. Couriers have no move out of {@code PAUSED} or into it, and
 *       none from {@code CANCELLED}. {@link Parcel#changeStatus} asks this table.</li>
 *   <li><b>Merchant hold moves</b> (amendment): before pickup only, {@code CREATED} to {@code PAUSED}
 *       or {@code CANCELLED}, {@code PAUSED} back to {@code CREATED} or on to {@code CANCELLED}.
 *       {@link Parcel#pause}, {@link Parcel#resume} and {@link Parcel#cancel} ask this table.</li>
 * </ul>
 *
 * <p>{@code DELIVERED}, {@code FAILED} and {@code CANCELLED} are final ({@link ParcelStatus#isFinal}).
 */
public final class ParcelLifecycle {

    private static final Map<ParcelStatus, Set<ParcelStatus>> COURIER_MOVES = new EnumMap<>(ParcelStatus.class);
    private static final Map<ParcelStatus, Set<ParcelStatus>> MERCHANT_HOLD_MOVES =
            new EnumMap<>(ParcelStatus.class);

    static {
        COURIER_MOVES.put(ParcelStatus.CREATED, EnumSet.of(ParcelStatus.PICKED_UP));
        COURIER_MOVES.put(ParcelStatus.PICKED_UP, EnumSet.of(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED));
        COURIER_MOVES.put(ParcelStatus.IN_TRANSIT, EnumSet.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED));
        COURIER_MOVES.put(ParcelStatus.OUT_FOR_DELIVERY, EnumSet.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED));
        COURIER_MOVES.put(ParcelStatus.DELIVERED, EnumSet.noneOf(ParcelStatus.class));
        COURIER_MOVES.put(ParcelStatus.FAILED, EnumSet.noneOf(ParcelStatus.class));
        // Held and cancelled parcels are the merchant's to release or close, never the courier's
        COURIER_MOVES.put(ParcelStatus.PAUSED, EnumSet.noneOf(ParcelStatus.class));
        COURIER_MOVES.put(ParcelStatus.CANCELLED, EnumSet.noneOf(ParcelStatus.class));

        for (ParcelStatus status : ParcelStatus.values()) {
            MERCHANT_HOLD_MOVES.put(status, EnumSet.noneOf(ParcelStatus.class));
        }
        MERCHANT_HOLD_MOVES.get(ParcelStatus.CREATED).addAll(EnumSet.of(ParcelStatus.PAUSED, ParcelStatus.CANCELLED));
        MERCHANT_HOLD_MOVES.get(ParcelStatus.PAUSED).addAll(EnumSet.of(ParcelStatus.CREATED, ParcelStatus.CANCELLED));
    }

    private ParcelLifecycle() {
    }

    /** Whether a courier may move a parcel from {@code from} to {@code to}. */
    public static boolean canMove(ParcelStatus from, ParcelStatus to) {
        return COURIER_MOVES.get(from).contains(to);
    }

    /** Whether the owning merchant may hold, release or cancel a parcel from {@code from} to {@code to}. */
    public static boolean canHoldMove(ParcelStatus from, ParcelStatus to) {
        return MERCHANT_HOLD_MOVES.get(from).contains(to);
    }

    public static boolean isTerminal(ParcelStatus status) {
        return status.isFinal();
    }

    /** Statuses a courier can move a parcel to next; empty for final, held and cancelled parcels. */
    public static Set<ParcelStatus> nextStatuses(ParcelStatus from) {
        return Set.copyOf(COURIER_MOVES.get(from));
    }
}
