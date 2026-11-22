package com.swiftdrop.dispatch.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** The hub a dispatcher works for: one row per dispatcher user, so one dispatcher has one hub. */
@Entity
@Table(name = "dispatcher_hubs")
public class DispatcherHub {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "hub_id", nullable = false)
    private UUID hubId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DispatcherHub() {
        // for JPA
    }

    public DispatcherHub(UUID userId, UUID hubId, Instant now) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.hubId = Objects.requireNonNull(hubId, "hubId must not be null");
        Instant timestamp = Objects.requireNonNull(now, "now must not be null");
        this.createdAt = timestamp;
        this.updatedAt = timestamp;
    }

    /** Moves the dispatcher to another hub at {@code now}. */
    public void moveTo(UUID newHubId, Instant now) {
        this.hubId = Objects.requireNonNull(newHubId, "hubId must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getHubId() {
        return hubId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "DispatcherHub[userId=" + userId + ", hubId=" + hubId + "]";
    }
}
