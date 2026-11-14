package com.swiftdrop.merchant.domain;

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
 * An individual webhook delivery record tracking an event transmission attempt to a merchant endpoint (MER-3).
 */
@Entity
@Table(name = "merchant_webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookDelivery() {
        // for JPA
    }

    public WebhookDelivery(UUID endpointId, UUID merchantId, UUID parcelId, UUID eventId) {
        this.endpointId = Objects.requireNonNull(endpointId, "endpointId");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.attemptCount = 0;
        this.status = "PENDING";
        Instant now = Instant.now();
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void recordSuccess(int httpStatus) {
        this.status = "SUCCESS";
        this.lastHttpStatus = httpStatus;
        this.lastAttemptAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void recordRetry(int httpStatus, Instant nextAttempt) {
        this.attemptCount++;
        this.status = "PENDING";
        this.lastHttpStatus = httpStatus;
        this.lastAttemptAt = Instant.now();
        this.nextAttemptAt = Objects.requireNonNull(nextAttempt, "nextAttempt");
        this.updatedAt = Instant.now();
    }

    public void recordFailure(int httpStatus) {
        this.attemptCount++;
        this.status = "FAILED";
        this.lastHttpStatus = httpStatus;
        this.lastAttemptAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getStatus() {
        return status;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

