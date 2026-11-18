package com.swiftdrop.dispatch.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One courier assignment decided by a dispatcher. Active while {@code endedAt} is null; the database
 * allows only one active row per parcel and leg. IDs only: names and contact data stay in
 * {@code identity}.
 */
@Entity
@Table(name = "parcel_assignments")
public class AssignmentRecord {

    /** Why an assignment ended. */
    public enum EndReason {
        /** Another courier was assigned to the same leg. */
        REASSIGNED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leg_type", nullable = false, updatable = false, length = 20)
    private LegType legType;

    @Column(name = "courier_user_id", nullable = false, updatable = false)
    private UUID courierUserId;

    @Column(name = "dispatcher_user_id", nullable = false, updatable = false)
    private UUID dispatcherUserId;

    @Column(name = "hub_id", nullable = false, updatable = false)
    private UUID hubId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private EndReason endReason;

    protected AssignmentRecord() {
        // for JPA
    }

    public AssignmentRecord(UUID parcelId, LegType legType, UUID courierUserId, UUID dispatcherUserId, UUID hubId,
            Instant now) {
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId must not be null");
        this.legType = Objects.requireNonNull(legType, "legType must not be null");
        this.courierUserId = Objects.requireNonNull(courierUserId, "courierUserId must not be null");
        this.dispatcherUserId = Objects.requireNonNull(dispatcherUserId, "dispatcherUserId must not be null");
        this.hubId = Objects.requireNonNull(hubId, "hubId must not be null");
        this.assignedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /** Ends this assignment at {@code now}; ending twice is a bug, not a no-op. */
    public void end(EndReason reason, Instant now) {
        if (endedAt != null) {
            throw new IllegalStateException("Assignment already ended");
        }
        this.endReason = Objects.requireNonNull(reason, "reason must not be null");
        this.endedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public LegType getLegType() {
        return legType;
    }

    public UUID getCourierUserId() {
        return courierUserId;
    }

    public UUID getDispatcherUserId() {
        return dispatcherUserId;
    }

    public UUID getHubId() {
        return hubId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public EndReason getEndReason() {
        return endReason;
    }

    @Override
    public String toString() {
        return "AssignmentRecord[parcelId=" + parcelId + ", legType=" + legType + ", active=" + isActive() + "]";
    }
}
