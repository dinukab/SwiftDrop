package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class MerchantParcelControlServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000002");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID MERCHANT_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String REFERENCE = "ORD-10045";
    private static final Instant NOW = Instant.parse("2026-11-15T10:15:30Z");
    private static final Actor ACTOR = new Actor(MERCHANT_USER_ID, ActorRole.MERCHANT);

    private ParcelRepository parcels;
    private ParcelEventRecorder recorder;
    private MerchantParcelControlService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        recorder = mock(ParcelEventRecorder.class);
        service = new MerchantParcelControlService(parcels, recorder, Clock.fixed(NOW, ZoneOffset.UTC));
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        parcel = Parcel.forMerchant("SD7K3M9Q2XWB", MERCHANT_ID, REFERENCE, address, address, ParcelSize.SMALL, 1000,
                null, new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW.minusSeconds(60));
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
        when(parcels.findByMerchantIdAndMerchantOrderReference(MERCHANT_ID, REFERENCE))
                .thenReturn(Optional.of(parcel));
        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
    }

    @Test
    void pauseHoldsTheParcelUnderTheLockAndRecordsTheMerchantUserAndReason() {
        ParcelStatus status = service.pause(MERCHANT_ID, REFERENCE, MERCHANT_USER_ID, "  Still packing  ");

        assertThat(status).isEqualTo(ParcelStatus.PAUSED);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PAUSED);
        assertThat(parcel.getLastSequence()).isEqualTo(2);
        verify(parcels).findByIdForUpdate(PARCEL_ID);
        verify(recorder).record(parcel, ParcelStatus.CREATED, ACTOR, "Still packing", null, NOW);
    }

    @Test
    void resumeReleasesTheHold() {
        service.pause(MERCHANT_ID, REFERENCE, MERCHANT_USER_ID, null);

        ParcelStatus status = service.resume(MERCHANT_ID, REFERENCE, MERCHANT_USER_ID, null);

        assertThat(status).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.getLastSequence()).isEqualTo(3);
        verify(recorder).record(parcel, ParcelStatus.PAUSED, ACTOR, null, null, NOW);
    }

    @Test
    void cancelClosesTheParcelFromCreatedOrPaused() {
        service.pause(MERCHANT_ID, REFERENCE, MERCHANT_USER_ID, null);

        ParcelStatus status = service.cancel(MERCHANT_ID, REFERENCE, MERCHANT_USER_ID, "Out of stock");

        assertThat(status).isEqualTo(ParcelStatus.CANCELLED);
        verify(recorder).record(parcel, ParcelStatus.PAUSED, ACTOR, "Out of stock", null, NOW);
    }

    @Test
    void theOrderReferenceIsTrimmedAndABlankReasonIsDropped() {
        service.pause(MERCHANT_ID, "  " + REFERENCE + " ", MERCHANT_USER_ID, "   ");

        verify(recorder).record(parcel, ParcelStatus.CREATED, ACTOR, null, null, NOW);
    }

    @Test
    void unknownOrReferenceOfAnotherMerchantIsNotFoundAndNothingIsRecorded() {
        when(parcels.findByMerchantIdAndMerchantOrderReference(any(), eq("ORD-OTHER"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(MERCHANT_ID, "ORD-OTHER", MERCHANT_USER_ID, null))
                .isInstanceOf(ParcelNotFoundException.class);
        verify(recorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aParcelPastCreatedIsAConflictAndNothingIsRecorded() {
        parcel.assignCourier(COURIER_ID, NOW);
        parcel.changeStatus(ParcelStatus.PICKED_UP, COURIER_ID, null, false, NOW);

        assertThatThrownBy(() -> service.pause(MERCHANT_ID, REFERENCE, MERCHANT_USER_ID, null))
                .isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> service.cancel(MERCHANT_ID, REFERENCE, MERCHANT_USER_ID, null))
                .isInstanceOf(ParcelConflictException.class);
        verify(recorder, never()).record(any(), any(), any(), any(), isNull(), any());
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
    }

    @Test
    void aReasonOver500CharactersIsRejectedBeforeAnythingChanges() {
        String tooLong = "x".repeat(501);

        assertThatThrownBy(() -> service.pause(MERCHANT_ID, REFERENCE, MERCHANT_USER_ID, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        verify(recorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void allIdsAreRequired() {
        assertThatThrownBy(() -> service.pause(null, REFERENCE, MERCHANT_USER_ID, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.pause(MERCHANT_ID, null, MERCHANT_USER_ID, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.pause(MERCHANT_ID, REFERENCE, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
