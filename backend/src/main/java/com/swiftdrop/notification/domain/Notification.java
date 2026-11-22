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
 * One mock SMS stored for a user account (ADR 0004 section 8). The text names the tracking number and the status
 * only; there is no phone number and no address.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public static final int MAX_MESSAGE_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "tracking_number", nullable = false, updatable = false, length = 20)
    private String trackingNumber;

    /** The event that caused it: one event creates at most one notification. */
    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;

    @Column(name = "status", nullable = false, updatable = false, length = 30)
    private String status;

    @Column(name = "message", nullable = false, updatable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // for JPA
    }

    public Notification(UUID userId, UUID parcelId, String trackingNumber, UUID eventId, String status,
            String message, Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        this.trackingNumber = Objects.requireNonNull(trackingNumber, "trackingNumber");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.status = Objects.requireNonNull(status, "status");
        this.message = Objects.requireNonNull(message, "message");
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must be at most " + MAX_MESSAGE_LENGTH + " characters");
        }
        this.createdAt = Objects.requireNonNull(now, "now");
    }

    /** Marks the notification read; reading a read notification keeps the first time. */
    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = Objects.requireNonNull(now, "now");
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    /** Never includes the message text. */
    @Override
    public String toString() {
        return "Notification[id=" + id + ", userId=" + userId + ", status=" + status + ", read=" + isRead() + "]";
    }
}
