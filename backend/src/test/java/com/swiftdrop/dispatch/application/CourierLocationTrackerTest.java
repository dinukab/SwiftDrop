package com.swiftdrop.dispatch.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.delivery.api.LegView;
import com.swiftdrop.delivery.api.ParcelLegs;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.VehicleType;

class CourierLocationTrackerTest {

    private static final UUID PARCEL = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID UVA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COLOMBO = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID PICKUP_COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID HUB_COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID PROFILE = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private ParcelLegs parcelLegs;
    private CourierProfileService profileService;
    private CourierLocationTracker tracker;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        parcelLegs = mock(ParcelLegs.class);
        profileService = mock(CourierProfileService.class);
        ObjectFactory<ParcelLegs> legFactory = mock(ObjectFactory.class);
        ObjectFactory<CourierProfileService> profileFactory = mock(ObjectFactory.class);
        when(legFactory.getObject()).thenReturn(parcelLegs);
        when(profileFactory.getObject()).thenReturn(profileService);
        tracker = new CourierLocationTracker(legFactory, profileFactory);
    }

    private static LegView leg(int sequence, LegType type, UUID from, UUID to, UUID courier, Instant completedAt) {
        return new LegView(UUID.randomUUID(), PARCEL, sequence, type, from, to, courier, NOW, completedAt);
    }

    private static ParcelStatusChanged event(ParcelStatus status) {
        return ParcelStatusChanged.of(UUID.randomUUID(), NOW, new ParcelStatusChangedData(PARCEL, "SD7K3M9Q2XWB",
                ParcelStatus.PICKED_UP, status, 2, ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, null));
    }

    private CourierProfile hubCourierProfile() {
        CourierProfile profile = new CourierProfile(HUB_COURIER, "+94771234567", VehicleType.BIKE, NOW);
        ReflectionTestUtils.setField(profile, "id", PROFILE);
        return profile;
    }

    @Test
    void theCourierWhoFinishedTheLegIsMovedToItsDestinationHub() {
        when(parcelLegs.findLegs(PARCEL)).thenReturn(List.of(
                leg(1, LegType.PICKUP, UVA, UVA, PICKUP_COURIER, NOW.minusSeconds(600)),
                leg(2, LegType.INTER_HUB, UVA, COLOMBO, HUB_COURIER, NOW),
                leg(3, LegType.FINAL_MILE, COLOMBO, COLOMBO, null, null)));
        when(profileService.findByUserId(HUB_COURIER)).thenReturn(Optional.of(hubCourierProfile()));

        tracker.onStatusChanged(event(ParcelStatus.HUB_ARRIVED));

        verify(profileService).moveToHub(PROFILE, COLOMBO);
    }

    @Test
    void aHubToHubLegReceivedByADispatcherMovesNoCourier() {
        // The earlier pickup courier must not be moved when the receiving dispatcher confirms the transfer
        when(parcelLegs.findLegs(PARCEL)).thenReturn(List.of(
                leg(1, LegType.PICKUP, UVA, UVA, PICKUP_COURIER, NOW.minusSeconds(600)),
                leg(2, LegType.INTER_HUB, UVA, COLOMBO, null, NOW),
                leg(3, LegType.FINAL_MILE, COLOMBO, COLOMBO, null, null)));

        tracker.onStatusChanged(event(ParcelStatus.HUB_ARRIVED));

        verify(profileService, never()).findByUserId(any());
        verify(profileService, never()).moveToHub(any(), any());
    }

    @Test
    void otherStatusChangesAndParcelsWithoutLegsChangeNothing() {
        tracker.onStatusChanged(event(ParcelStatus.OUT_FOR_DELIVERY));
        verify(parcelLegs, never()).findLegs(any());

        when(parcelLegs.findLegs(PARCEL)).thenReturn(List.of());
        tracker.onStatusChanged(event(ParcelStatus.HUB_ARRIVED));
        verify(profileService, never()).moveToHub(any(), any());
    }

    @Test
    void aCourierWithoutAProfileOrAFailingUpdateNeverBreaksTheArrival() {
        when(parcelLegs.findLegs(PARCEL)).thenReturn(List.of(
                leg(1, LegType.PICKUP, UVA, UVA, PICKUP_COURIER, NOW)));
        when(profileService.findByUserId(PICKUP_COURIER)).thenReturn(Optional.empty());
        tracker.onStatusChanged(event(ParcelStatus.HUB_ARRIVED));
        verify(profileService, never()).moveToHub(any(), any());

        when(profileService.findByUserId(PICKUP_COURIER)).thenReturn(Optional.of(hubCourierProfile()));
        when(profileService.moveToHub(PROFILE, UVA)).thenThrow(new HubNotFoundException());
        tracker.onStatusChanged(event(ParcelStatus.HUB_ARRIVED)); // must not throw
    }
}
