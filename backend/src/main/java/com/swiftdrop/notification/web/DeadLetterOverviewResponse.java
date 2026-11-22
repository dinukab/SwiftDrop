package com.swiftdrop.notification.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.notification.application.DeadLetter;
import com.swiftdrop.notification.application.DeadLetterReplayService;

/** The dead-letter queue as an administrator sees it: IDs, status and the broker's reason, never the body. */
record DeadLetterOverviewResponse(String queue, long depth, List<Item> oldest) {

    static DeadLetterOverviewResponse from(DeadLetterReplayService.Overview overview) {
        return new DeadLetterOverviewResponse(overview.queue(), overview.depth(),
                overview.oldest().stream().map(Item::from).toList());
    }

    record Item(UUID eventId, String status, String reason, long deathCount, Instant deadLetteredAt) {

        static Item from(DeadLetter letter) {
            return new Item(letter.eventId(), letter.status(), letter.reason(), letter.deathCount(),
                    letter.deadLetteredAt());
        }
    }
}
