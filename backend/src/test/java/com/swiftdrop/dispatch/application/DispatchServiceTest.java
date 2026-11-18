package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

class DispatchServiceTest {

    private static final UUID PARCEL = UUID.fromString("5a0e0000-0000-0000-0000-000000000003");
    private static final UUID COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DISPATCHER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private ParcelQueries parcelQueries;
    private ParcelAssignment parcelAssignment;
    private CourierEligibility eligibility;
    private CourierProfileRepository profiles;
    private UserDirectory users;
    private DispatchService service;

    @BeforeEach
    void setUp() {
        parcelQueries = mock(ParcelQueries.class);
        parcelAssignment = mock(ParcelAssignment.class);
        eligibility = mock(CourierEligibility.class);
        profiles = mock(CourierProfileRepository.class);
        users = mock(UserDirectory.class);
        service = new DispatchService(parcelQueries, parcelAssignment, eligibility, profiles, users);
    }

    @Test
    void assignChecksEligibilityThenRecordsTheAssignment() {
        service.assign(PARCEL, COURIER, DISPATCHER);

        var order = org.mockito.Mockito.inOrder(eligibility, parcelAssignment);
        order.verify(eligibility).requireEligible(COURIER);
        order.verify(parcelAssignment).assignCourier(PARCEL, COURIER, DISPATCHER);
    }

    @Test
    void anIneligibleCourierIsNeverAssigned() {
        doThrow(new CourierNotEligibleException("Courier profile is not active"))
                .when(eligibility).requireEligible(COURIER);

        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(CourierNotEligibleException.class);
        verify(parcelAssignment, never()).assignCourier(any(), any(), any());
    }

    @Test
    void aParcelPastCreatedIsLeftToDeliveryToRefuse() {
        doThrow(new ParcelConflictException("A courier can only be assigned before pickup"))
                .when(parcelAssignment).assignCourier(PARCEL, COURIER, DISPATCHER);

        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void unassignedDelegatesToDeliveryWithThePage() {
        PageResult<com.swiftdrop.delivery.api.ParcelSummary> page = new PageResult<>(List.of(), 2, 10, 0, 0);
        when(parcelQueries.findUnassignedParcels(any())).thenReturn(page);

        assertThat(service.unassigned(2, 10)).isEqualTo(page);
    }

    @Test
    void workloadsListActiveCouriersLeastBusyFirstAndCountTheMissingAsZero() {
        UUID busy = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID idle = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        UUID none = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
        when(profiles.findByActive(org.mockito.ArgumentMatchers.eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(
                        profile(busy, VehicleType.VAN), profile(idle, VehicleType.CAR), profile(none, VehicleType.BIKE))));
        when(users.findUser(busy)).thenReturn(Optional.of(user(busy, "Busy Bill")));
        when(users.findUser(idle)).thenReturn(Optional.of(user(idle, "Idle Ida")));
        when(users.findUser(none)).thenReturn(Optional.of(user(none, "New Nimal")));
        when(parcelQueries.countActiveParcelsByCourier()).thenReturn(Map.of(busy, 3L, idle, 1L));

        List<DispatchService.CourierWorkload> loads = service.workloads();

        assertThat(loads).extracting(DispatchService.CourierWorkload::name)
                .containsExactly("New Nimal", "Idle Ida", "Busy Bill");
        assertThat(loads).extracting(DispatchService.CourierWorkload::activeParcels)
                .containsExactly(0L, 1L, 3L);
    }

    @Test
    void workloadsLeaveOutCouriersThatAssignmentWouldRefuse() {
        UUID ok = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        UUID disabled = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        UUID customer = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
        UUID gone = UUID.fromString("00000000-0000-0000-0000-0000000000b4");
        when(profiles.findByActive(org.mockito.ArgumentMatchers.eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(
                        profile(ok, VehicleType.BIKE), profile(disabled, VehicleType.BIKE),
                        profile(customer, VehicleType.BIKE), profile(gone, VehicleType.BIKE))));
        when(users.findUser(ok)).thenReturn(Optional.of(user(ok, "Eligible Eli")));
        when(users.findUser(disabled)).thenReturn(Optional.of(
                new UserSummary(disabled, "Disabled Dan", "dan@example.com", Set.of("COURIER"), false)));
        when(users.findUser(customer)).thenReturn(Optional.of(
                new UserSummary(customer, "Plain Pat", "pat@example.com", Set.of("CUSTOMER"), true)));
        when(users.findUser(gone)).thenReturn(Optional.empty());
        when(parcelQueries.countActiveParcelsByCourier()).thenReturn(Map.of());

        assertThat(service.workloads()).extracting(DispatchService.CourierWorkload::courierUserId)
                .containsExactly(ok);
    }

    @Test
    void theWorkloadViewAsksOnlyForActiveProfiles() {
        when(profiles.findByActive(org.mockito.ArgumentMatchers.eq(true), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.workloads()).isEmpty();
        verify(profiles).findByActive(org.mockito.ArgumentMatchers.eq(true), any());
    }

    private static CourierProfile profile(UUID userId, VehicleType vehicle) {
        return new CourierProfile(userId, "+94771234567", vehicle, Instant.parse("2026-11-15T10:00:00Z"));
    }

    private static UserSummary user(UUID id, String name) {
        return new UserSummary(id, name, name.replace(' ', '.').toLowerCase() + "@example.com",
                Set.of("COURIER"), true);
    }
}
