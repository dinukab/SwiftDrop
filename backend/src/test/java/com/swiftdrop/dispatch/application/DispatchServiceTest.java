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
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.AssignmentRecord;
import com.swiftdrop.dispatch.domain.Hub;
import com.swiftdrop.dispatch.domain.LegType;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.pricing.api.ParcelSize;

class DispatchServiceTest {

    private static final UUID PARCEL = UUID.fromString("5a0e0000-0000-0000-0000-000000000003");
    private static final UUID COURIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DISPATCHER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private ParcelQueries parcelQueries;
    private ParcelAssignment parcelAssignment;
    private CourierEligibility eligibility;
    private CourierProfileRepository profiles;
    private UserDirectory users;
    private HubService hubService;
    private AssignmentHistoryService historyService;
    private Hub uva;
    private DispatchService service;

    @BeforeEach
    void setUp() {
        parcelQueries = mock(ParcelQueries.class);
        parcelAssignment = mock(ParcelAssignment.class);
        eligibility = mock(CourierEligibility.class);
        profiles = mock(CourierProfileRepository.class);
        users = mock(UserDirectory.class);
        hubService = mock(HubService.class);
        historyService = mock(AssignmentHistoryService.class);
        uva = hub("LK-UP");
        service = new DispatchService(parcelQueries, parcelAssignment, eligibility, profiles, users,
                () -> hubService, () -> historyService);
        // By default the dispatcher works for the Uva hub and the parcel waits for pickup there
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.of(uva));
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn("LK-UP")));
    }

    private static Hub hub(String code) {
        Hub hub = BeanUtils.instantiateClass(Hub.class);
        ReflectionTestUtils.setField(hub, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(hub, "code", code);
        ReflectionTestUtils.setField(hub, "name", code + " Hub");
        ReflectionTestUtils.setField(hub, "active", true);
        return hub;
    }

    private static ParcelSummary parcelPickedUpIn(String provinceCode) {
        return new ParcelSummary(PARCEL, "SD1234567890", ParcelStatus.CREATED, ParcelSize.SMALL, 500,
                ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, null, Instant.parse("2026-11-17T10:00:00Z"),
                provinceCode, "LK-SP");
    }

    @Test
    void assignChecksTheHubThenEligibilityThenRecordsTheAssignment() {
        service.assign(PARCEL, COURIER, DISPATCHER);

        var order = org.mockito.Mockito.inOrder(parcelQueries, eligibility, parcelAssignment);
        order.verify(parcelQueries).findParcelById(PARCEL);
        order.verify(eligibility).requireEligible(COURIER);
        order.verify(parcelAssignment).assignCourier(PARCEL, COURIER, DISPATCHER);
    }

    @Test
    void aParcelFromAnotherHubLooksLikeAMissingParcelAndNothingIsAssigned() {
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn("LK-SP")));

        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
        verify(eligibility, never()).requireEligible(any());
        verify(parcelAssignment, never()).assignCourier(any(), any(), any());
    }

    @Test
    void anUnknownParcelIsNotFound() {
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
        verify(parcelAssignment, never()).assignCourier(any(), any(), any());
    }

    @Test
    void aParcelWithAnUnknownPickupProvinceIsNotAssignable() {
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn(null)));

        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void aDispatcherWithoutAHubCannotAssignOrListAnything() {
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(DispatcherHubNotAssignedException.class);
        assertThatThrownBy(() -> service.unassigned(DISPATCHER, 0, 10))
                .isInstanceOf(DispatcherHubNotAssignedException.class);
        verify(parcelQueries, never()).findParcelById(any());
        verify(parcelQueries, never()).findUnassignedParcels(any(), any());
        verify(parcelAssignment, never()).assignCourier(any(), any(), any());
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
    void theQueueIsFilteredByTheDispatchersHubCode() {
        PageResult<ParcelSummary> page = new PageResult<>(List.of(), 2, 10, 0, 0);
        when(parcelQueries.findUnassignedParcels(any(), any())).thenReturn(page);

        assertThat(service.unassigned(DISPATCHER, 2, 10)).isEqualTo(page);

        verify(parcelQueries).findUnassignedParcels(new PageRequest(2, 10), "LK-UP");
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

    @Test
    void aSuccessfulAssignmentIsRecordedInTheHistoryAfterDeliveryAcceptedIt() {
        service.assign(PARCEL, COURIER, DISPATCHER);

        var order = org.mockito.Mockito.inOrder(parcelAssignment, historyService);
        order.verify(parcelAssignment).assignCourier(PARCEL, COURIER, DISPATCHER);
        order.verify(historyService).record(PARCEL, COURIER, DISPATCHER, uva.getId());
    }

    @Test
    void nothingIsRecordedWhenDeliveryRefusesTheAssignment() {
        doThrow(new ParcelConflictException("A courier can only be assigned before pickup"))
                .when(parcelAssignment).assignCourier(PARCEL, COURIER, DISPATCHER);

        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(ParcelConflictException.class);
        verify(historyService, never()).record(any(), any(), any(), any());
    }

    @Test
    void nothingIsRecordedForAnotherHubsParcelOrAnIneligibleCourier() {
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn("LK-SP")));
        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);

        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn("LK-UP")));
        doThrow(new CourierNotEligibleException("Courier profile is not active"))
                .when(eligibility).requireEligible(COURIER);
        assertThatThrownBy(() -> service.assign(PARCEL, COURIER, DISPATCHER))
                .isInstanceOf(CourierNotEligibleException.class);

        verify(historyService, never()).record(any(), any(), any(), any());
    }

    @Test
    void theHistoryNamesWhoAssignedWhomAndFlagsTheEndedAssignment() {
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
        Instant t1 = Instant.parse("2026-11-18T09:00:00Z");
        Instant t2 = Instant.parse("2026-11-18T09:30:00Z");
        AssignmentRecord first = new AssignmentRecord(PARCEL, LegType.PICKUP, COURIER, DISPATCHER, uva.getId(), t1);
        first.end(AssignmentRecord.EndReason.REASSIGNED, t2);
        AssignmentRecord second = new AssignmentRecord(PARCEL, LegType.PICKUP, other, DISPATCHER, uva.getId(), t2);
        when(historyService.history(PARCEL)).thenReturn(List.of(first, second));
        when(hubService.requireHub(uva.getId())).thenReturn(uva);
        when(users.findUser(COURIER)).thenReturn(Optional.of(user(COURIER, "Kamal Perera")));
        when(users.findUser(other)).thenReturn(Optional.of(user(other, "Nimal Silva")));
        when(users.findUser(DISPATCHER)).thenReturn(Optional.of(
                new UserSummary(DISPATCHER, "Dina Dispatcher", "dina@example.com", Set.of("DISPATCHER"), true)));

        List<DispatchService.AssignmentHistoryEntry> entries = service.assignmentHistory(PARCEL, DISPATCHER);

        assertThat(entries).extracting(DispatchService.AssignmentHistoryEntry::courierName)
                .containsExactly("Kamal Perera", "Nimal Silva");
        assertThat(entries).extracting(DispatchService.AssignmentHistoryEntry::dispatcherName)
                .containsOnly("Dina Dispatcher");
        assertThat(entries).extracting(DispatchService.AssignmentHistoryEntry::hubCode).containsOnly("LK-UP");
        assertThat(entries.get(0).endReason()).isEqualTo("REASSIGNED");
        assertThat(entries.get(0).endedAt()).isEqualTo(t2);
        assertThat(entries.get(1).endedAt()).isNull();
        assertThat(entries.get(1).endReason()).isNull();
        assertThat(entries.get(0).legType()).isEqualTo("PICKUP");
    }

    @Test
    void theHistoryOfAnotherHubsParcelLooksLikeAMissingParcel() {
        when(parcelQueries.findParcelById(PARCEL)).thenReturn(Optional.of(parcelPickedUpIn("LK-SP")));

        assertThatThrownBy(() -> service.assignmentHistory(PARCEL, DISPATCHER))
                .isInstanceOf(ParcelNotFoundException.class);
        verify(historyService, never()).history(any());
    }

    @Test
    void theHistoryNeedsADispatcherWithAHub() {
        when(hubService.hubForDispatcher(DISPATCHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignmentHistory(PARCEL, DISPATCHER))
                .isInstanceOf(DispatcherHubNotAssignedException.class);
    }
}
