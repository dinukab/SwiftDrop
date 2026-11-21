package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ShipmentLeg;
import com.swiftdrop.delivery.domain.ShipmentLegRepository;

class LegProgressTest {

    private static final UUID PARCEL = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID UVA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COLOMBO = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID SOUTHERN = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private ShipmentLegRepository repository;
    private LegProgress progress;
    private ShipmentLeg pickup;
    private ShipmentLeg toColombo;
    private ShipmentLeg toSouthern;
    private ShipmentLeg finalMile;

    @BeforeEach
    void setUp() {
        repository = mock(ShipmentLegRepository.class);
        progress = new LegProgress(repository);
        // Uva to Southern: pickup, Uva to Colombo, Colombo to Southern, final mile
        pickup = new ShipmentLeg(PARCEL, 1, LegType.PICKUP, UVA, UVA, NOW);
        toColombo = new ShipmentLeg(PARCEL, 2, LegType.INTER_HUB, UVA, COLOMBO, NOW);
        toSouthern = new ShipmentLeg(PARCEL, 3, LegType.INTER_HUB, COLOMBO, SOUTHERN, NOW);
        finalMile = new ShipmentLeg(PARCEL, 4, LegType.FINAL_MILE, SOUTHERN, SOUTHERN, NOW);
        when(repository.findByParcelIdOrderBySequenceAsc(PARCEL)).thenReturn(List.of(pickup, toColombo, toSouthern, finalMile));
    }

    @Test
    void thePickupCourierAlsoHoldsTheFirstLeg() {
        progress.syncPickupCourier(PARCEL, COURIER, NOW);

        assertThat(pickup.getAssignedCourierId()).isEqualTo(COURIER);
        assertThat(toColombo.isAssigned()).isFalse();
    }

    @Test
    void aParcelWithoutLegsIsLeftAlone() {
        UUID other = UUID.randomUUID();
        when(repository.findByParcelIdOrderBySequenceAsc(other)).thenReturn(List.of());

        progress.syncPickupCourier(other, COURIER, NOW);
        progress.arrivedAtHub(other, null, NOW);
        progress.finished(other, NOW);
    }

    @Test
    void arrivalAtTheOriginHubEndsThePickupLeg() {
        progress.arrivedAtHub(PARCEL, UVA, NOW);

        assertThat(pickup.isCompleted()).isTrue();
        assertThat(toColombo.isCompleted()).isFalse();
    }

    @Test
    void eachArrivalEndsTheLegInProgressInOrder() {
        progress.arrivedAtHub(PARCEL, UVA, NOW);
        progress.arrivedAtHub(PARCEL, COLOMBO, NOW);
        progress.arrivedAtHub(PARCEL, SOUTHERN, NOW);

        assertThat(List.of(pickup, toColombo, toSouthern)).allMatch(ShipmentLeg::isCompleted);
        assertThat(finalMile.isCompleted()).isFalse();
    }

    @Test
    void arrivalAtTheWrongHubIsRefusedAndNothingEnds() {
        assertThatThrownBy(() -> progress.arrivedAtHub(PARCEL, COLOMBO, NOW))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("This is not the destination hub of the current leg");
        assertThat(pickup.isCompleted()).isFalse();
    }

    @Test
    void arrivalMustNameTheHubWhenThereAreLegs() {
        assertThatThrownBy(() -> progress.arrivedAtHub(PARCEL, null, NOW))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void theFinalLegEndsWithTheDeliveryNotAtAHub() {
        pickup.complete(NOW);
        toColombo.complete(NOW);
        toSouthern.complete(NOW);

        assertThatThrownBy(() -> progress.arrivedAtHub(PARCEL, SOUTHERN, NOW))
                .isInstanceOf(ParcelConflictException.class);

        progress.finished(PARCEL, NOW);
        assertThat(finalMile.isCompleted()).isTrue();
    }

    @Test
    void whenEveryLegIsCompleteAnotherArrivalIsRefusedAndFinishingChangesNothing() {
        List.of(pickup, toColombo, toSouthern, finalMile).forEach(leg -> leg.complete(NOW));

        assertThatThrownBy(() -> progress.arrivedAtHub(PARCEL, SOUTHERN, NOW))
                .isInstanceOf(ParcelConflictException.class);
        progress.finished(PARCEL, NOW.plusSeconds(60));
        assertThat(finalMile.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void thePickupCourierMayPickUpAndArriveButNotDeliver() {
        pickup.assignCourier(COURIER, NOW);

        progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
        progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.PICKED_UP, ParcelStatus.HUB_ARRIVED);
        progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.PICKED_UP, ParcelStatus.FAILED);
        assertThatThrownBy(() -> progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.HUB_ARRIVED,
                ParcelStatus.OUT_FOR_DELIVERY)).isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void thePickupCourierHoldsNoLegOnceTheParcelReachedTheHub() {
        pickup.assignCourier(COURIER, NOW);
        pickup.complete(NOW);

        assertThatThrownBy(() -> progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.HUB_ARRIVED,
                ParcelStatus.OUT_FOR_DELIVERY))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("The courier holds no open leg of this parcel");
    }

    @Test
    void theFinalMileCourierMayDeliverOrFailButNotPickUpOrArrive() {
        pickup.complete(NOW);
        toColombo.complete(NOW);
        toSouthern.complete(NOW);
        finalMile.assignCourier(COURIER, NOW);

        progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.HUB_SORTED, ParcelStatus.OUT_FOR_DELIVERY);
        progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);
        progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED);
        assertThatThrownBy(() -> progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.HUB_SORTED,
                ParcelStatus.IN_TRANSIT)).isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> progress.requireMoveAllowed(PARCEL, COURIER, ParcelStatus.HUB_ARRIVED,
                ParcelStatus.FAILED)).isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void aParcelWithoutLegsKeepsTheSingleCourierRules() {
        UUID other = UUID.randomUUID();
        when(repository.findByParcelIdOrderBySequenceAsc(other)).thenReturn(List.of());

        progress.requireMoveAllowed(other, COURIER, ParcelStatus.HUB_SORTED, ParcelStatus.IN_TRANSIT);
    }
}
