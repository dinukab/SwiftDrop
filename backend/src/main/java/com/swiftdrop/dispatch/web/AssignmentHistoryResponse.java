package com.swiftdrop.dispatch.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.dispatch.application.DispatchService.AssignmentHistoryEntry;

/**
 * One assignment in a parcel's history. Courier and dispatcher are named for the dispatcher's screen;
 * {@code endedAt} and {@code endReason} are null while the assignment is active.
 */
record AssignmentHistoryResponse(
        UUID id,
        String legType,
        UUID courierUserId,
        String courierName,
        UUID dispatcherUserId,
        String dispatcherName,
        String hubCode,
        Instant assignedAt,
        Instant endedAt,
        String endReason,
        boolean active) {

    static AssignmentHistoryResponse from(AssignmentHistoryEntry entry) {
        return new AssignmentHistoryResponse(entry.id(), entry.legType(), entry.courierUserId(), entry.courierName(),
                entry.dispatcherUserId(), entry.dispatcherName(), entry.hubCode(), entry.assignedAt(),
                entry.endedAt(), entry.endReason(), entry.endedAt() == null);
    }
}
