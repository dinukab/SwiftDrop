package com.swiftdrop.merchant.application;

import java.time.Instant;
import java.util.UUID;

/**
 * A new API key, returned once. {@code signingSecret} is null when encryption is not configured on the
 * server, in which case the key can be used without request signatures only.
 */
public record GeneratedApiKey(
        UUID id,
        String label,
        String keyPrefix,
        String rawKey,
        String signingSecret,
        Instant createdAt
) {
    /** Never prints the raw key or the signing secret. */
    @Override
    public String toString() {
        return "GeneratedApiKey[id=" + id + ", label=" + label + ", prefix=" + keyPrefix + ", ***]";
    }
}
