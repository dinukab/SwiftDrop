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
 * An HTTPS webhook endpoint registered by a merchant for receiving real-time shipment updates (MER-3).
 */
@Entity
@Table(name = "merchant_webhook_endpoints")
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "secret_encrypted", nullable = false, columnDefinition = "TEXT")
    private String secretEncrypted;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpoint() {
        // for JPA
    }

    public WebhookEndpoint(UUID merchantId, String url, String secretEncrypted) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.url = Objects.requireNonNull(url, "url").trim();
        this.secretEncrypted = Objects.requireNonNull(secretEncrypted, "secretEncrypted");
        this.active = true;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void updateUrl(String url) {
        this.url = Objects.requireNonNull(url, "url").trim();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getUrl() {
        return url;
    }

    public String getSecretEncrypted() {
        return secretEncrypted;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

