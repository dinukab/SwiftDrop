package com.swiftdrop.merchant.domain;

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
 * Merchant profile aggregate (MER-1, ADR 0003). Stores merchant registration details and
 * approval state.
 *
 * <p>Cross-module reference to the owning user account is by {@code userId} alone; no direct
 * JPA relationship to {@code User} (ADR 0003).
 */
@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "registration_number", nullable = false, unique = true, length = 50)
    private String registrationNumber;

    @Column(name = "contact_email", nullable = false, length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Merchant() {
        // for JPA
    }

    public Merchant(UUID userId, String businessName, String registrationNumber, String contactEmail,
            String contactPhone) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.businessName = Objects.requireNonNull(businessName, "businessName").trim();
        this.registrationNumber = Objects.requireNonNull(registrationNumber, "registrationNumber").trim();
        this.contactEmail = Objects.requireNonNull(contactEmail, "contactEmail").trim();
        this.contactPhone = Objects.requireNonNull(contactPhone, "contactPhone").trim();
        this.status = MerchantStatus.PENDING_APPROVAL;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void approve(UUID adminUserId) {
        this.status = MerchantStatus.APPROVED;
        this.reviewedBy = Objects.requireNonNull(adminUserId, "adminUserId");
        this.reviewedAt = Instant.now();
        this.rejectionReason = null;
        this.updatedAt = Instant.now();
    }

    public void reject(UUID adminUserId, String reason) {
        this.status = MerchantStatus.REJECTED;
        this.reviewedBy = Objects.requireNonNull(adminUserId, "adminUserId");
        this.reviewedAt = Instant.now();
        this.rejectionReason = reason != null ? reason.trim() : null;
        this.updatedAt = Instant.now();
    }

    public void suspend(UUID adminUserId) {
        this.status = MerchantStatus.SUSPENDED;
        this.reviewedBy = Objects.requireNonNull(adminUserId, "adminUserId");
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isApproved() {
        return this.status == MerchantStatus.APPROVED;
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

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getContactEmail() {
        return contactEmail;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

