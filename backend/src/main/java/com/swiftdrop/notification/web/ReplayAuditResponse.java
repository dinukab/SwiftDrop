package com.swiftdrop.notification.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.notification.domain.DeadLetterReplay;

/** One audit row: who replayed which event, when, and why it had been dead-lettered. */
record ReplayAuditResponse(UUID id, String queue, UUID eventId, String originalReason, UUID replayedBy,
        Instant replayedAt) {

    static ReplayAuditResponse from(DeadLetterReplay replay) {
        return new ReplayAuditResponse(replay.getId(), replay.getQueue(), replay.getEventId(),
                replay.getOriginalReason(), replay.getReplayedBy(), replay.getReplayedAt());
    }
}
