package com.swiftdrop.merchant.application;

import java.time.Instant;
import java.util.UUID;

public record GeneratedApiKey(
        UUID id,
        String label,
        String keyPrefix,
        String rawKey,
        Instant createdAt
) {
    /** Never prints the raw key in toString. */
    @Override
    public String toString() {
        return "GeneratedApiKey[id=" + id + ", label=" + label + ", prefix=" + keyPrefix + ", ***]";
    }
}

