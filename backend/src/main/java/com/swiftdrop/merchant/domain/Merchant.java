package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Merchant profile owned by the merchant module. Registration creates a {@link MerchantStatus#PENDING}
 * row alongside a MERCHANT identity user; capabilities require {@link MerchantStatus#APPROVED} (D-22).
 */
@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MerchantStatus status;

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

    protected Merchant() {
        // JPA
    }

    public Merchant(UUID userId, String businessName, String contactPhone) {
        this.userId = userId;
        this.businessName = businessName.trim();
        this.contactPhone = normalizePhone(contactPhone);
        this.status = MerchantStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public MerchantStatus getStatus() {
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
        return status == MerchantStatus.APPROVED;
    }

    public void approve(UUID adminId) {
        this.status = MerchantStatus.APPROVED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = null;
    }

    public void reject(UUID adminId, String reason) {
        this.status = MerchantStatus.REJECTED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = reason.trim();
    }

    public void suspend(UUID adminId) {
        this.status = MerchantStatus.SUSPENDED;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
    }

    private static String normalizePhone(String contactPhone) {
        if (contactPhone == null) {
            return null;
        }
        String trimmed = contactPhone.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "Merchant[id=" + id + ", status=" + status + "]";
    }
}
