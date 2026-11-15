package com.swiftdrop.delivery.domain;

import static com.swiftdrop.delivery.api.ParcelStatus.CANCELLED;
import static com.swiftdrop.delivery.api.ParcelStatus.CREATED;
import static com.swiftdrop.delivery.api.ParcelStatus.DELIVERED;
import static com.swiftdrop.delivery.api.ParcelStatus.FAILED;
import static com.swiftdrop.delivery.api.ParcelStatus.IN_TRANSIT;
import static com.swiftdrop.delivery.api.ParcelStatus.OUT_FOR_DELIVERY;
import static com.swiftdrop.delivery.api.ParcelStatus.PAUSED;
import static com.swiftdrop.delivery.api.ParcelStatus.PICKED_UP;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Every one of the 64 status pairs against the two transition tables of ADR 0006: the courier moves
 * of §1 and the merchant hold moves of the pause/cancel amendment.
 */
class ParcelLifecycleTest {

    /** Courier moves. Deliberately unchanged by the amendment: no courier move touches PAUSED or CANCELLED. */
    private static final Set<String> COURIER_ALLOWED = Set.of(
            "CREATED->PICKED_UP",
            "PICKED_UP->IN_TRANSIT",
            "PICKED_UP->FAILED",
            "IN_TRANSIT->OUT_FOR_DELIVERY",
            "IN_TRANSIT->FAILED",
            "OUT_FOR_DELIVERY->DELIVERED",
            "OUT_FOR_DELIVERY->FAILED");

    /** Merchant hold moves, before pickup only. */
    private static final Set<String> MERCHANT_ALLOWED = Set.of(
            "CREATED->PAUSED",
            "CREATED->CANCELLED",
            "PAUSED->CREATED",
            "PAUSED->CANCELLED");

    static Stream<Arguments> allPairs() {
        return Stream.of(ParcelStatus.values())
                .flatMap(from -> Stream.of(ParcelStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "courier {0} -> {1}")
    @MethodSource("allPairs")
    void onlyTheAdrCourierTransitionsAreAllowed(ParcelStatus from, ParcelStatus to) {
        assertThat(ParcelLifecycle.canMove(from, to)).isEqualTo(COURIER_ALLOWED.contains(from + "->" + to));
    }

    @ParameterizedTest(name = "merchant {0} -> {1}")
    @MethodSource("allPairs")
    void onlyTheMerchantHoldTransitionsAreAllowed(ParcelStatus from, ParcelStatus to) {
        assertThat(ParcelLifecycle.canHoldMove(from, to)).isEqualTo(MERCHANT_ALLOWED.contains(from + "->" + to));
    }

    @Test
    void aCourierCanNeverMoveIntoOrOutOfPausedOrCancelled() {
        for (ParcelStatus other : ParcelStatus.values()) {
            assertThat(ParcelLifecycle.canMove(other, PAUSED)).as(other + " -> PAUSED").isFalse();
            assertThat(ParcelLifecycle.canMove(other, CANCELLED)).as(other + " -> CANCELLED").isFalse();
            assertThat(ParcelLifecycle.canMove(PAUSED, other)).as("PAUSED -> " + other).isFalse();
            assertThat(ParcelLifecycle.canMove(CANCELLED, other)).as("CANCELLED -> " + other).isFalse();
        }
    }

    @Test
    void aMerchantCanNeverTouchAParcelAfterPickup() {
        for (ParcelStatus afterPickup : Set.of(PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED)) {
            for (ParcelStatus target : ParcelStatus.values()) {
                assertThat(ParcelLifecycle.canHoldMove(afterPickup, target))
                        .as(afterPickup + " -> " + target).isFalse();
            }
        }
        for (ParcelStatus target : ParcelStatus.values()) {
            assertThat(ParcelLifecycle.canHoldMove(CANCELLED, target)).as("CANCELLED -> " + target).isFalse();
        }
    }

    @Test
    void deliveredFailedAndCancelledAreTheOnlyTerminalStatuses() {
        assertThat(Stream.of(ParcelStatus.values()).filter(ParcelLifecycle::isTerminal))
                .containsExactlyInAnyOrder(DELIVERED, FAILED, CANCELLED);
    }

    @Test
    void aPausedParcelIsNotTerminal() {
        assertThat(ParcelLifecycle.isTerminal(PAUSED)).isFalse();
        assertThat(ParcelLifecycle.isTerminal(CREATED)).isFalse();
    }

    @Test
    void nextStatusesMatchTheCourierTable() {
        assertThat(ParcelLifecycle.nextStatuses(CREATED)).containsExactly(PICKED_UP);
        assertThat(ParcelLifecycle.nextStatuses(PICKED_UP)).containsExactlyInAnyOrder(IN_TRANSIT, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(IN_TRANSIT)).containsExactlyInAnyOrder(OUT_FOR_DELIVERY, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(OUT_FOR_DELIVERY)).containsExactlyInAnyOrder(DELIVERED, FAILED);
        assertThat(ParcelLifecycle.nextStatuses(DELIVERED)).isEmpty();
        assertThat(ParcelLifecycle.nextStatuses(FAILED)).isEmpty();
        // A courier is offered no move for a held or cancelled parcel
        assertThat(ParcelLifecycle.nextStatuses(PAUSED)).isEmpty();
        assertThat(ParcelLifecycle.nextStatuses(CANCELLED)).isEmpty();
    }
}
