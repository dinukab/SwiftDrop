package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.dispatch.domain.AssignmentRecord;
import com.swiftdrop.dispatch.domain.AssignmentRecordRepository;
import com.swiftdrop.dispatch.domain.LegType;

class AssignmentHistoryServiceTest {

    private static final UUID PARCEL = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID COURIER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COURIER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID DISPATCHER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID HUB = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private AssignmentRecordRepository records;
    private AssignmentHistoryService service;

    @BeforeEach
    void setUp() {
        records = mock(AssignmentRecordRepository.class);
        service = new AssignmentHistoryService(records, Clock.fixed(NOW, ZoneOffset.UTC));
        when(records.saveAndFlush(any(AssignmentRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void theFirstAssignmentStartsAnActivePickupRecord() {
        when(records.findByParcelIdAndLegTypeAndEndedAtIsNull(PARCEL, LegType.PICKUP)).thenReturn(Optional.empty());

        AssignmentRecord created = service.record(PARCEL, COURIER_A, DISPATCHER, HUB);

        assertThat(created.getParcelId()).isEqualTo(PARCEL);
        assertThat(created.getLegType()).isEqualTo(LegType.PICKUP);
        assertThat(created.getCourierUserId()).isEqualTo(COURIER_A);
        assertThat(created.getDispatcherUserId()).isEqualTo(DISPATCHER);
        assertThat(created.getHubId()).isEqualTo(HUB);
        assertThat(created.getAssignedAt()).isEqualTo(NOW);
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void aReassignmentEndsThePreviousRecordBeforeStartingTheNewOne() {
        AssignmentRecord previous = new AssignmentRecord(PARCEL, LegType.PICKUP, COURIER_A, DISPATCHER, HUB,
                NOW.minusSeconds(600));
        when(records.findByParcelIdAndLegTypeAndEndedAtIsNull(PARCEL, LegType.PICKUP))
                .thenReturn(Optional.of(previous));

        AssignmentRecord created = service.record(PARCEL, COURIER_B, DISPATCHER, HUB);

        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getEndReason()).isEqualTo(AssignmentRecord.EndReason.REASSIGNED);
        assertThat(previous.getEndedAt()).isEqualTo(NOW);
        assertThat(created.getCourierUserId()).isEqualTo(COURIER_B);
        // The old row is flushed first, or the partial unique index would see two active rows
        var order = inOrder(records);
        order.verify(records).saveAndFlush(previous);
        order.verify(records).saveAndFlush(created);
    }

    @Test
    void assigningTheCourierWhoAlreadyHoldsTheLegChangesNothing() {
        AssignmentRecord current = new AssignmentRecord(PARCEL, LegType.PICKUP, COURIER_A, DISPATCHER, HUB,
                NOW.minusSeconds(600));
        when(records.findByParcelIdAndLegTypeAndEndedAtIsNull(PARCEL, LegType.PICKUP))
                .thenReturn(Optional.of(current));

        assertThat(service.record(PARCEL, COURIER_A, DISPATCHER, HUB)).isSameAs(current);

        assertThat(current.isActive()).isTrue();
        verify(records, never()).saveAndFlush(any());
    }

    @Test
    void aRaceLostAtTheDatabaseBecomesAConflict() {
        when(records.findByParcelIdAndLegTypeAndEndedAtIsNull(PARCEL, LegType.PICKUP)).thenReturn(Optional.empty());
        when(records.saveAndFlush(any(AssignmentRecord.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.record(PARCEL, COURIER_A, DISPATCHER, HUB))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void anEndedRecordCannotBeEndedAgain() {
        AssignmentRecord record = new AssignmentRecord(PARCEL, LegType.PICKUP, COURIER_A, DISPATCHER, HUB, NOW);
        record.end(AssignmentRecord.EndReason.REASSIGNED, NOW);

        assertThatThrownBy(() -> record.end(AssignmentRecord.EndReason.REASSIGNED, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void historyComesFromTheRepositoryOldestFirst() {
        AssignmentRecord one = new AssignmentRecord(PARCEL, LegType.PICKUP, COURIER_A, DISPATCHER, HUB, NOW);
        when(records.findByParcelIdOrderByAssignedAtAscIdAsc(PARCEL)).thenReturn(List.of(one));

        assertThat(service.history(PARCEL)).containsExactly(one);
    }
}
