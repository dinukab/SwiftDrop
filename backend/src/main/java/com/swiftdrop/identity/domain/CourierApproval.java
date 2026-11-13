package com.swiftdrop.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Approval state of a courier account created through public registration. Admin-created
 * couriers have no row and are treated as approved. The COURIER authority is withheld from
 * access tokens until the status is {@link CourierApprovalStatus#APPROVED}.
 */
@Entity
@Table(name = "courier_approvals")
public class CourierApproval {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourierApprovalStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CourierApproval() {
        // JPA
    }

    public CourierApproval(UUID userId) {
        this.userId = userId;
        this.status = CourierApprovalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public CourierApprovalStatus getStatus() {
        return status;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isApproved() {
        return status == CourierApprovalStatus.APPROVED;
    }

    public void approve(UUID adminId) {
        this.status = CourierApprovalStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = null;
    }

    public void reject(UUID adminId, String reason) {
        this.status = CourierApprovalStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = reason.trim();
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "CourierApproval[userId=" + userId + ", status=" + status + "]";
    }
}
