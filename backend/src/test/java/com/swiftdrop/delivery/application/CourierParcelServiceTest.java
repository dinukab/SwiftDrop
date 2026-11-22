package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.delivery.api.LegView;
import com.swiftdrop.delivery.api.ParcelLegs;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.CourierParcelService.CourierParcel;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class CourierParcelServiceTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final DeliveryProofRepository proofs = mock(DeliveryProofRepository.class);
    private final ParcelLegs legs = mock(ParcelLegs.class);

    @Test
    void todayStartsAtMidnightInColomboNotUtc() {
        // 01:00 on 4 Nov in Colombo is still 3 Nov in UTC
        CourierParcelService service = at("2026-11-03T19:30:00Z");

        assertThat(service.startOfToday()).isEqualTo(Instant.parse("2026-11-03T18:30:00Z"));
    }

    @Test
    void nextStatusesFollowTheLegTheCourierHolds() {
        CourierParcelService service = at("2026-11-03T10:00:00Z");
        Parcel atHub = parcel(ParcelStatus.HUB_ARRIVED);
        Parcel afterHandover = parcel(ParcelStatus.HUB_ARRIVED);
        UUID hub = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-03T09:00:00Z");
        // Pickup courier: the leg ended at the hub, so no moves are left
        when(legs.findLegs(atHub.getId())).thenReturn(List.of(
                new LegView(UUID.randomUUID(), atHub.getId(), 1, LegType.PICKUP, hub, hub, COURIER_ID, now, now),
                new LegView(UUID.randomUUID(), atHub.getId(), 2, LegType.FINAL_MILE, hub, hub, null, null, null)));
        // Final-mile courier: only the pickup from the hub, not the sorting or inter-hub moves
        when(legs.findLegs(afterHandover.getId())).thenReturn(List.of(
                new LegView(UUID.randomUUID(), afterHandover.getId(), 1, LegType.PICKUP, hub, hub,
                        UUID.randomUUID(), now, now),
                new LegView(UUID.randomUUID(), afterHandover.getId(), 2, LegType.FINAL_MILE, hub, hub, COURIER_ID, now,
                        null)));
        when(parcels.findCourierDay(eq(COURIER_ID), any(), any())).thenReturn(List.of(atHub, afterHandover));
        when(proofs.findParcelIdsWithProof(any())).thenReturn(Set.of());

        List<CourierParcel> today = service.today(COURIER_ID);

        assertThat(today.get(0).nextStatuses()).isEmpty();
        assertThat(today.get(1).nextStatuses()).containsExactly(ParcelStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void justBeforeColomboMidnightBelongsToThePreviousDay() {
        CourierParcelService service = at("2026-11-03T18:29:59Z");

        assertThat(service.startOfToday()).isEqualTo(Instant.parse("2026-11-02T18:30:00Z"));
    }

    @Test
    void listAsksForUnfinishedOrFinishedTodayAndMarksProofAndNextStatuses() {
        CourierParcelService service = at("2026-11-03T10:00:00Z");
        Parcel outForDelivery = parcel(ParcelStatus.OUT_FOR_DELIVERY);
        Parcel delivered = parcel(ParcelStatus.DELIVERED);
        when(parcels.findCourierDay(eq(COURIER_ID), any(), any())).thenReturn(List.of(outForDelivery, delivered));
        when(proofs.findParcelIdsWithProof(any())).thenReturn(Set.of(outForDelivery.getId()));

        List<CourierParcel> today = service.today(COURIER_ID);

        assertThat(today).extracting(CourierParcel::proofCaptured).containsExactly(true, false);
        assertThat(today.get(0).nextStatuses()).containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
        assertThat(today.get(1).nextStatuses()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ParcelStatus>> terminal = ArgumentCaptor.forClass(Collection.class);
        verify(parcels).findCourierDay(eq(COURIER_ID), terminal.capture(), eq(Instant.parse("2026-11-02T18:30:00Z")));
        // A cancelled parcel leaves the courier's list like a delivered or failed one; a paused one stays
        assertThat(terminal.getValue())
                .containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED, ParcelStatus.CANCELLED);
    }

    @Test
    void emptyDaySkipsTheProofQuery() {
        when(parcels.findCourierDay(any(), any(), any())).thenReturn(List.of());

        assertThat(at("2026-11-03T10:00:00Z").today(COURIER_ID)).isEmpty();
        verifyNoInteractions(proofs);
    }

    private CourierParcelService at(String instant) {
        return new CourierParcelService(parcels, proofs, () -> legs, COLOMBO, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static Parcel parcel(ParcelStatus status) {
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        Parcel parcel = Parcel.forCustomer("SD7K3M9Q2XWB", UUID.randomUUID(), address, address, ParcelSize.SMALL, 1000,
                null, new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), Instant.now());
        ReflectionTestUtils.setField(parcel, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(parcel, "status", status);
        return parcel;
    }
}
