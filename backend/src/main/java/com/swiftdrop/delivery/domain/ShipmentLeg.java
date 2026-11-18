package com.swiftdrop.delivery.domain;

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

import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.delivery.api.ParcelConflictException;

/**
 * One segment of a parcel's hub-based journey. For hub-based delivery, a parcel moves through one
 * or more legs: pickup courier brings to origin hub, inter-hub transport, destination courier
 * delivers. Each leg has its own courier assignment and may transition independently.
 */
@Entity
@Table(name = "shipment_legs")
public class ShipmentLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "leg_type", nullable = false, updatable = false, length = 20)
    private LegType legType;

    @Column(name = "source_hub_id", nullable = false, updatable = false)
    private UUID sourceHubId;

    @Column(name = "destination_hub_id", nullable = false, updatable = false)
    private UUID destinationHubId;

    @Column(name = "assigned_courier_id")
    private UUID assignedCourierId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ShipmentLeg() {
        // for JPA
    }

    public ShipmentLeg(UUID parcelId, int sequence, LegType legType, UUID sourceHubId,
                       UUID destinationHubId, Instant now) {
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId must not be null");
        this.sequence = sequence;
        this.legType = Objects.requireNonNull(legType, "legType must not be null");
        this.sourceHubId = Objects.requireNonNull(sourceHubId, "sourceHubId must not be null");
        this.destinationHubId = Objects.requireNonNull(destinationHubId, "destinationHubId must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * Assigns a courier user to this leg, or replaces the one assigned.
     *
     * @throws ParcelConflictException if the leg is already complete
     */
    public void assignCourier(UUID courierId, Instant now) {
        if (completedAt != null) {
            throw new ParcelConflictException("The leg is already complete");
        }
        this.assignedCourierId = Objects.requireNonNull(courierId, "courierId must not be null");
        this.assignedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public int getSequence() {
        return sequence;
    }

    public LegType getLegType() {
        return legType;
    }

    public UUID getSourceHubId() {
        return sourceHubId;
    }

    public UUID getDestinationHubId() {
        return destinationHubId;
    }

    public UUID getAssignedCourierId() {
        return assignedCourierId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    /** Ends the leg at {@code now}; ending an ended leg changes nothing. */
    public void complete(Instant now) {
        if (completedAt == null) {
            this.completedAt = Objects.requireNonNull(now, "now must not be null");
        }
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isAssigned() {
        return assignedCourierId != null;
    }

    @Override
    public String toString() {
        return "ShipmentLeg[parcelId=" + parcelId
                + ", sequence=" + sequence
                + ", legType=" + legType
                + ", assigned=" + (assignedCourierId != null)
                + "]";
    }
}
