package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSummary;

class DispatchServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID DISPATCHER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private ParcelQueries queries;
    private ParcelAssignment assignment;
    private CourierEligibility eligibility;
    private DispatchService service;

    @BeforeEach
    void setUp() {
        queries = mock(ParcelQueries.class);
        assignment = mock(ParcelAssignment.class);
        eligibility = mock(CourierEligibility.class);
        service = new DispatchService(queries, assignment, eligibility);
    }

    @Test
    void unassignedClampsPagingAndDelegates() {
        PageResult<ParcelSummary> empty = new PageResult<>(List.of(), 0, 100, 0, 0);
        when(queries.findUnassignedParcels(any())).thenReturn(empty);

        assertThat(service.unassigned(-3, 5000)).isSameAs(empty);

        ArgumentCaptor<PageRequest> page = ArgumentCaptor.forClass(PageRequest.class);
        verify(queries).findUnassignedParcels(page.capture());
        assertThat(page.getValue()).isEqualTo(new PageRequest(0, 100));
    }

    @Test
    void assignsAfterEligibilityCheck() {
        service.assign(PARCEL_ID, COURIER_ID, DISPATCHER_ID);

        verify(eligibility).requireEligible(COURIER_ID);
        verify(assignment).assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID);
    }

    @Test
    void ineligibleCourierNeverReachesDelivery() {
        doThrow(new CourierNotEligibleException("Courier profile is inactive"))
                .when(eligibility).requireEligible(COURIER_ID);

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, DISPATCHER_ID))
                .isInstanceOf(CourierNotEligibleException.class);
        verify(assignment, never()).assignCourier(any(), any(), any());
    }

    @Test
    void deliveryConflictPropagates() {
        doThrow(new ParcelConflictException("Parcel can no longer be assigned"))
                .when(assignment).assignCourier(PARCEL_ID, COURIER_ID, DISPATCHER_ID);

        assertThatThrownBy(() -> service.assign(PARCEL_ID, COURIER_ID, DISPATCHER_ID))
                .isInstanceOf(ParcelConflictException.class);
    }
}
