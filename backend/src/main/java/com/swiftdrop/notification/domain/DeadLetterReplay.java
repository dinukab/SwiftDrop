package com.swiftdrop.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Audit row for one dead-lettered message that an administrator moved back to its source queue (ADR 0004 section 6).
 * It holds IDs and the broker's reason only, never the message body.
 */
@Entity
@Table(name = "notification_dead_letter_replays")
public class DeadLetterReplay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "queue", nullable = false, updatable = false, length = 100)
    private String queue;

    /** Null if the dead letter could not be parsed. */
    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "original_reason", updatable = false, length = 50)
    private String originalReason;

    @Column(name = "replayed_by", nullable = false, updatable = false)
    private UUID replayedBy;

    @Column(name = "replayed_at", nullable = false, updatable = false)
    private Instant replayedAt;

    protected DeadLetterReplay() {
        // for JPA
    }

    public DeadLetterReplay(String queue, UUID eventId, String originalReason, UUID replayedBy, Instant replayedAt) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.eventId = eventId;
        this.originalReason = originalReason == null ? null : truncate(originalReason, 50);
        this.replayedBy = Objects.requireNonNull(replayedBy, "replayedBy");
        this.replayedAt = Objects.requireNonNull(replayedAt, "replayedAt");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public String getQueue() {
        return queue;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getOriginalReason() {
        return originalReason;
    }

    public UUID getReplayedBy() {
        return replayedBy;
    }

    public Instant getReplayedAt() {
        return replayedAt;
    }
}
