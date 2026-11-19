package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.LegPlan;
import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.delivery.api.LegView;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ShipmentLeg;
import com.swiftdrop.delivery.domain.ShipmentLegRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class ShipmentLegServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID UVA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COLOMBO = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID SOUTHERN = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID PICKUP_COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID HUB_COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID DISPATCHER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private ParcelRepository parcels;
    private ShipmentLegRepository legRepository;
    private ParcelEventRecorder recorder;
    private ShipmentLegService service;
    private Parcel parcel;
    private ShipmentLeg pickup;
    private ShipmentLeg toColombo;
    private ShipmentLeg finalMile;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        legRepository = mock(ShipmentLegRepository.class);
        recorder = mock(ParcelEventRecorder.class);
        service = new ShipmentLegService(parcels, legRepository, recorder, Clock.fixed(NOW, ZoneOffset.UTC));
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Badulla",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));

        // Uva to Colombo: pickup, Uva to Colombo, final mile; the pickup leg is done and the parcel is at Uva
        pickup = leg(1, LegType.PICKUP, UVA, UVA);
        toColombo = leg(2, LegType.INTER_HUB, UVA, COLOMBO);
        finalMile = leg(3, LegType.FINAL_MILE, COLOMBO, COLOMBO);
        pickup.assignCourier(PICKUP_COURIER, NOW.minusSeconds(3000));
        pickup.complete(NOW.minusSeconds(60));
        when(legRepository.findByParcelIdOrderBySequenceAsc(PARCEL_ID)).thenReturn(List.of(pickup, toColombo, finalMile));
        when(legRepository.findByIdAndParcelId(toColombo.getId(), PARCEL_ID)).thenReturn(Optional.of(toColombo));
        when(legRepository.findByIdAndParcelId(pickup.getId(), PARCEL_ID)).thenReturn(Optional.of(pickup));
        when(legRepository.findByIdAndParcelId(finalMile.getId(), PARCEL_ID)).thenReturn(Optional.of(finalMile));
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.HUB_SORTED);
        ReflectionTestUtils.setField(parcel, "assignedCourierId", PICKUP_COURIER);
    }

    private ShipmentLeg leg(int sequence, LegType type, UUID source, UUID destination) {
        ShipmentLeg leg = new ShipmentLeg(PARCEL_ID, sequence, type, source, destination, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(leg, "id", UUID.randomUUID());
        return leg;
    }

    private static List<LegPlan> uvaToSouthern() {
        return List.of(
                new LegPlan(LegType.PICKUP, UVA, UVA),
                new LegPlan(LegType.INTER_HUB, UVA, COLOMBO),
                new LegPlan(LegType.INTER_HUB, COLOMBO, SOUTHERN),
                new LegPlan(LegType.FINAL_MILE, SOUTHERN, SOUTHERN));
    }

    @Test
    void plansLegsInOrderForAParcelBeforePickup() {
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.CREATED);
        when(legRepository.existsByParcelId(PARCEL_ID)).thenReturn(false);

        assertThat(service.planLegs(PARCEL_ID, uvaToSouthern())).isTrue();

        ArgumentCaptor<ShipmentLeg> saved = ArgumentCaptor.forClass(ShipmentLeg.class);
        verify(legRepository, times(4)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ShipmentLeg::getSequence).containsExactly(1, 2, 3, 4);
        assertThat(saved.getAllValues()).extracting(ShipmentLeg::getLegType)
                .containsExactly(LegType.PICKUP, LegType.INTER_HUB, LegType.INTER_HUB, LegType.FINAL_MILE);
        assertThat(saved.getAllValues().get(2).getSourceHubId()).isEqualTo(COLOMBO);
        assertThat(saved.getAllValues().get(2).getDestinationHubId()).isEqualTo(SOUTHERN);
    }

    @Test
    void planningTwiceIsHarmlessButAfterPickupOrWithAMalformedRouteItIsRefused() {
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.CREATED);
        when(legRepository.existsByParcelId(PARCEL_ID)).thenReturn(true);
        // Already planned (for example by a concurrent assignment): nothing changes, and it is not an error
        assertThat(service.planLegs(PARCEL_ID, uvaToSouthern())).isFalse();
        verify(legRepository, never()).save(any());

        when(legRepository.existsByParcelId(PARCEL_ID)).thenReturn(false);
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.PICKED_UP);
        assertThatThrownBy(() -> service.planLegs(PARCEL_ID, uvaToSouthern()))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Legs can only be planned before pickup");

        assertThatThrownBy(() -> service.planLegs(PARCEL_ID, List.of(new LegPlan(LegType.PICKUP, UVA, UVA))))
                .isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> service.planLegs(PARCEL_ID, List.of(
                new LegPlan(LegType.FINAL_MILE, UVA, UVA), new LegPlan(LegType.PICKUP, UVA, UVA))))
                .isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> service.planLegs(PARCEL_ID, List.of(
                new LegPlan(LegType.PICKUP, UVA, UVA), new LegPlan(LegType.PICKUP, UVA, UVA),
                new LegPlan(LegType.FINAL_MILE, UVA, UVA))))
                .isInstanceOf(ParcelConflictException.class);
        verify(legRepository, never()).save(any());
    }

    @Test
    void anUnknownParcelCannotBePlanned() {
        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.planLegs(PARCEL_ID, uvaToSouthern()))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    /** The parcel was sent to Colombo and its receiving dispatcher confirmed it: the final leg may be assigned. */
    private void receivedAtColombo() {
        toColombo.markTransferred(DISPATCHER, NOW.minusSeconds(30));
        toColombo.markReceived(DISPATCHER, NOW.minusSeconds(10));
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.HUB_ARRIVED);
        ReflectionTestUtils.setField(parcel, "assignedCourierId", null);
        ReflectionTestUtils.setField(parcel, "assignedAt", null);
    }

    @Test
    void theFinalLegIsHandedToACourierOnlyAfterTheReceivingHubConfirmedTheParcel() {
        receivedAtColombo();

        LegView view = service.assignLegCourier(PARCEL_ID, finalMile.getId(), HUB_COURIER, DISPATCHER);

        assertThat(view.assignedCourierId()).isEqualTo(HUB_COURIER);
        assertThat(finalMile.getAssignedCourierId()).isEqualTo(HUB_COURIER);
        // The parcel is now held by the new courier, so every courier check of ADR 0006 follows them
        assertThat(parcel.getAssignedCourierId()).isEqualTo(HUB_COURIER);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.HUB_ARRIVED);
    }

    @Test
    void theFinalHandOverCanBeRepeatedUntilTheLegStarts() {
        receivedAtColombo();
        service.assignLegCourier(PARCEL_ID, finalMile.getId(), HUB_COURIER, DISPATCHER);
        UUID other = UUID.randomUUID();

        service.assignLegCourier(PARCEL_ID, finalMile.getId(), other, DISPATCHER);

        assertThat(parcel.getAssignedCourierId()).isEqualTo(other);
    }

    @Test
    void aHubToHubLegIsNeverGivenACourier() {
        assertThatThrownBy(() -> service.assignLegCourier(PARCEL_ID, toColombo.getId(), HUB_COURIER, DISPATCHER))
                .isInstanceOf(ParcelConflictException.class)
                .hasMessage("Hub-to-hub legs are transferred, not assigned to a courier");
        assertThat(toColombo.getAssignedCourierId()).isNull();
        assertThat(parcel.getAssignedCourierId()).isEqualTo(PICKUP_COURIER);
    }

    @Test
    void aTransferSendsTheParcelOnWithoutACourierAndIsNotAReceipt() {
        LegView view = service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER);

        assertThat(view.transferredAt()).isEqualTo(NOW);
        assertThat(view.transferredBy()).isEqualTo(DISPATCHER);
        // Sent, not received: the leg stays open and the parcel is on its way with nobody holding it
        assertThat(view.receivedAt()).isNull();
        assertThat(view.completedAt()).isNull();
        assertThat(view.assignedCourierId()).isNull();
        assertThat(toColombo.isCompleted()).isFalse();
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(parcel.getAssignedCourierId()).isNull();
        // Recorded in the history and the outbox by the dispatcher
        verify(recorder).record(parcel, ParcelStatus.HUB_SORTED, Actor.dispatcher(DISPATCHER), null, null, NOW);
        // The pickup courier lost access
        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.HUB_ARRIVED, PICKUP_COURIER, null, false, NOW))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void aParcelThatJustArrivedFromPickupCanBeTransferredWithoutBeingSortedFirst() {
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.HUB_ARRIVED);

        service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER);

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
    }

    @Test
    void aTransferCannotBeRepeated() {
        service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER);

        assertThatThrownBy(() -> service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel has already been transferred");
        verify(recorder, times(1)).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void nothingIsTransferredBeforeThePreviousLegIsReceivedOrWhileTheParcelIsNotAtAHub() {
        // The parcel has not been received at the sending hub yet
        ReflectionTestUtils.setField(pickup, "completedAt", null);
        assertThatThrownBy(() -> service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The previous leg is not complete yet");

        ReflectionTestUtils.setField(pickup, "completedAt", NOW.minusSeconds(60));
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.PICKED_UP);
        assertThatThrownBy(() -> service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel is not waiting at a hub");
        assertThat(toColombo.getTransferredAt()).isNull();
        verify(recorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void onlyAHubToHubLegWithoutACourierIsTransferred() {
        assertThatThrownBy(() -> service.transferLeg(PARCEL_ID, finalMile.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Only hub-to-hub legs are transferred");
        assertThatThrownBy(() -> service.transferLeg(PARCEL_ID, pickup.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class);

        // A leg that already has a courier (the old courier-driven way) is left to that courier
        toColombo.assignCourier(HUB_COURIER, NOW);
        assertThatThrownBy(() -> service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The leg is already assigned to a courier");
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.HUB_SORTED);
    }

    @Test
    void theReceivingHubConfirmsTheParcelWhichEndsTheLegAndMakesItReadyForTheNextOne() {
        service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER);
        UUID receiver = UUID.randomUUID();

        LegView view = service.confirmLegReceipt(PARCEL_ID, toColombo.getId(), receiver);

        assertThat(view.receivedAt()).isEqualTo(NOW);
        assertThat(view.receivedBy()).isEqualTo(receiver);
        assertThat(view.completedAt()).isEqualTo(NOW);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.HUB_ARRIVED);
        assertThat(parcel.getAssignedCourierId()).isNull();
        verify(recorder).record(parcel, ParcelStatus.IN_TRANSIT, Actor.dispatcher(receiver), null, null, NOW);
        // Now, and only now, the final leg can be given to a courier
        service.assignLegCourier(PARCEL_ID, finalMile.getId(), HUB_COURIER, DISPATCHER);
        assertThat(parcel.getAssignedCourierId()).isEqualTo(HUB_COURIER);
    }

    @Test
    void aParcelIsNotReceivedBeforeItWasSentOrTwice() {
        assertThatThrownBy(() -> service.confirmLegReceipt(PARCEL_ID, toColombo.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel has not been transferred yet");

        service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER);
        service.confirmLegReceipt(PARCEL_ID, toColombo.getId(), DISPATCHER);
        assertThatThrownBy(() -> service.confirmLegReceipt(PARCEL_ID, toColombo.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel has already been received");
        assertThatThrownBy(() -> service.confirmLegReceipt(PARCEL_ID, finalMile.getId(), DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("Only hub-to-hub legs are received");
        verify(recorder, times(2)).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void theFinalLegStaysClosedUntilTheParcelWasReceived() {
        service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER);

        // Sent but not received: the parcel is on its way, not at Colombo
        assertThatThrownBy(() -> service.assignLegCourier(PARCEL_ID, finalMile.getId(), HUB_COURIER, DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The previous leg is not complete yet");
        assertThat(parcel.getAssignedCourierId()).isNull();
    }

    @Test
    void transferAndReceiptOfAnUnknownParcelOrLegAreNotFound() {
        assertThatThrownBy(() -> service.transferLeg(PARCEL_ID, UUID.randomUUID(), DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> service.confirmLegReceipt(PARCEL_ID, UUID.randomUUID(), DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transferLeg(PARCEL_ID, toColombo.getId(), DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void thePickupLegIsNotAssignedThroughTheLegApi() {
        assertThatThrownBy(() -> service.assignLegCourier(PARCEL_ID, pickup.getId(), HUB_COURIER, DISPATCHER))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void aLegCannotBeAssignedBeforeThePreviousOneIsComplete() {
        // The final leg comes after the hub-to-hub leg, which is still open
        assertThatThrownBy(() -> service.assignLegCourier(PARCEL_ID, finalMile.getId(), HUB_COURIER, DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The previous leg is not complete yet");
        assertThat(parcel.getAssignedCourierId()).isEqualTo(PICKUP_COURIER);
    }

    @Test
    void theParcelMustWaitAtAHub() {
        receivedAtColombo();
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.OUT_FOR_DELIVERY);

        assertThatThrownBy(() -> service.assignLegCourier(PARCEL_ID, finalMile.getId(), HUB_COURIER, DISPATCHER))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel is not waiting at a hub");
        assertThat(parcel.getAssignedCourierId()).isNull();
    }

    @Test
    void aCompletedLegCannotBeReassigned() {
        receivedAtColombo();
        finalMile.assignCourier(HUB_COURIER, NOW);
        finalMile.complete(NOW);

        assertThatThrownBy(() -> service.assignLegCourier(PARCEL_ID, finalMile.getId(), PICKUP_COURIER, DISPATCHER))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void unknownParcelOrLegIsNotFound() {
        assertThatThrownBy(() -> service.assignLegCourier(PARCEL_ID, UUID.randomUUID(), HUB_COURIER, DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignLegCourier(PARCEL_ID, toColombo.getId(), HUB_COURIER, DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
    }
}
