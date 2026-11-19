package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.application.GeneratedApiKey;

/**
 * A new key, shown once. {@code signingSecret} signs the store's requests; it is null when the server
 * has no encryption key configured, and then the key cannot be used with signatures.
 */
public record GeneratedApiKeyResponse(
        UUID id,
        String label,
        String keyPrefix,
        String rawKey,
        String signingSecret,
        Instant createdAt
) {
    public static GeneratedApiKeyResponse from(GeneratedApiKey key) {
        return new GeneratedApiKeyResponse(
                key.id(),
                key.label(),
                key.keyPrefix(),
                key.rawKey(),
                key.signingSecret(),
                key.createdAt()
        );
    }

    /** Never outputs the raw API key or the signing secret in logs. */
    @Override
    public String toString() {
        return "GeneratedApiKeyResponse[id=" + id + ", label=" + label + ", prefix=" + keyPrefix + ", ***]";
    }
}
