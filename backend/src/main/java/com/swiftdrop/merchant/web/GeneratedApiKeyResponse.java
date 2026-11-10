package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.application.GeneratedApiKey;

public record GeneratedApiKeyResponse(
        UUID id,
        String label,
        String keyPrefix,
        String rawKey,
        Instant createdAt
) {
    public static GeneratedApiKeyResponse from(GeneratedApiKey key) {
        return new GeneratedApiKeyResponse(
                key.id(),
                key.label(),
                key.keyPrefix(),
                key.rawKey(),
                key.createdAt()
        );
    }

    /** Never outputs the raw API key in logs. */
    @Override
    public String toString() {
        return "GeneratedApiKeyResponse[id=" + id + ", label=" + label + ", prefix=" + keyPrefix + ", ***]";
    }
}

