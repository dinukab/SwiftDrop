package com.swiftdrop.delivery.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.swiftdrop.delivery.api.LegType;
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
 * <p>For a parcel with legs (ADR 0006 amendment, ADR 0007) the courier moves are narrowed again by the type of the
 * leg the courier holds, see {@link #canMove(LegType, ParcelStatus, ParcelStatus)}.
 *
 * <p>{@code DELIVERED}, {@code FAILED} and {@code CANCELLED} are final ({@link ParcelStatus#isFinal}).
 */
public final class ParcelLifecycle {

    private static final Map<ParcelStatus, Set<ParcelStatus>> COURIER_MOVES = new EnumMap<>(ParcelStatus.class);
    private static final Map<ParcelStatus, Set<ParcelStatus>> MERCHANT_HOLD_MOVES =
            new EnumMap<>(ParcelStatus.class);

    private static final Map<LegType, Map<ParcelStatus, Set<ParcelStatus>>> LEG_MOVES = new EnumMap<>(LegType.class);

    static {
        COURIER_MOVES.put(ParcelStatus.CREATED, EnumSet.of(ParcelStatus.PICKED_UP));
        COURIER_MOVES.put(ParcelStatus.PICKED_UP, EnumSet.of(ParcelStatus.HUB_ARRIVED, ParcelStatus.FAILED));
        COURIER_MOVES.put(ParcelStatus.HUB_ARRIVED,
                EnumSet.of(ParcelStatus.HUB_SORTED, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED));
        COURIER_MOVES.put(ParcelStatus.HUB_SORTED,
                EnumSet.of(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED));
        COURIER_MOVES.put(ParcelStatus.IN_TRANSIT, EnumSet.of(ParcelStatus.HUB_ARRIVED, ParcelStatus.FAILED));
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

    static {
        // Pickup courier: the leg ends when the parcel reaches the origin hub
        legMoves(LegType.PICKUP, ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        legMoves(LegType.PICKUP, ParcelStatus.PICKED_UP, ParcelStatus.HUB_ARRIVED, ParcelStatus.FAILED);
        // Final-mile courier: sorting is the hub's step, so it does not gate the courier
        legMoves(LegType.FINAL_MILE, ParcelStatus.HUB_ARRIVED, ParcelStatus.OUT_FOR_DELIVERY);
        legMoves(LegType.FINAL_MILE, ParcelStatus.HUB_SORTED, ParcelStatus.OUT_FOR_DELIVERY);
        legMoves(LegType.FINAL_MILE, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, ParcelStatus.FAILED);
        // Hub-to-hub legs belong to dispatchers; only a leg that got a courier before ADR 0007's amendment can
        // still be finished by that courier
        legMoves(LegType.INTER_HUB, ParcelStatus.HUB_ARRIVED, ParcelStatus.HUB_SORTED);
        legMoves(LegType.INTER_HUB, ParcelStatus.HUB_SORTED, ParcelStatus.IN_TRANSIT);
        legMoves(LegType.INTER_HUB, ParcelStatus.IN_TRANSIT, ParcelStatus.HUB_ARRIVED, ParcelStatus.FAILED);
    }

    private static void legMoves(LegType legType, ParcelStatus from, ParcelStatus... to) {
        LEG_MOVES.computeIfAbsent(legType, t -> new EnumMap<>(ParcelStatus.class))
                .computeIfAbsent(from, f -> EnumSet.noneOf(ParcelStatus.class))
                .addAll(Set.of(to));
    }

    private ParcelLifecycle() {
    }

    /** Whether a courier may move a parcel from {@code from} to {@code to}. */
    public static boolean canMove(ParcelStatus from, ParcelStatus to) {
        return COURIER_MOVES.get(from).contains(to);
    }

    /**
     * Whether the courier holding a leg of {@code legType} may move the parcel from {@code from} to {@code to}: the
     * move must be a courier move and one that leg type allows.
     */
    public static boolean canMove(LegType legType, ParcelStatus from, ParcelStatus to) {
        return canMove(from, to) && LEG_MOVES.get(legType).getOrDefault(from, Set.of()).contains(to);
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
