package com.swiftdrop.notification.web;

import java.util.List;
import java.util.UUID;

import com.swiftdrop.notification.application.DeadLetter;
import com.swiftdrop.notification.application.DeadLetterReplayService;

/**
 * What one replay request did. {@code stopReason} is {@code null} when it ended normally; otherwise a short category
 * (never broker text) telling why it stopped early. {@code eventIds} omits messages without a parseable event ID.
 */
record ReplayOutcomeResponse(int replayed, List<UUID> eventIds, String stopReason) {

    static ReplayOutcomeResponse from(DeadLetterReplayService.Outcome outcome) {
        return new ReplayOutcomeResponse(outcome.replayed().size(),
                outcome.replayed().stream().map(DeadLetter::eventId).filter(id -> id != null).toList(),
                outcome.stopReason());
    }
}
