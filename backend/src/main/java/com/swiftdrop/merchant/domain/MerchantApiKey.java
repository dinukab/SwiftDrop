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
 * An API key credential owned by an approved merchant for external API access (MER-2).
 *
 * <p>Raw key strings are never stored. Only the first 8 characters are retained as an indexed prefix
 * for lookup, alongside a BCrypt hash of the complete key.
 */
@Entity
@Table(name = "merchant_api_keys")
public class MerchantApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "key_prefix", nullable = false, length = 8, unique = true)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 255)
    private String keyHash;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MerchantApiKey() {
        // for JPA
    }

    public MerchantApiKey(UUID merchantId, String keyPrefix, String keyHash, String label) {
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.keyHash = Objects.requireNonNull(keyHash, "keyHash");
        this.label = Objects.requireNonNull(label, "label").trim();
        this.active = true;
        this.createdAt = Instant.now();
    }

    public void revoke() {
        this.active = false;
    }

    public void recordUsed(Instant at) {
        this.lastUsedAt = at != null ? at : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getLabel() {
        return label;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

