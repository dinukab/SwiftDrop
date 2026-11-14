package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.domain.MerchantApiKey;

public record ApiKeyResponse(
        UUID id,
        String label,
        String keyPrefix,
        boolean active,
        Instant lastUsedAt,
        Instant createdAt
) {
    public static ApiKeyResponse from(MerchantApiKey key) {
        return new ApiKeyResponse(
                key.getId(),
                key.getLabel(),
                key.getKeyPrefix(),
                key.isActive(),
                key.getLastUsedAt(),
                key.getCreatedAt()
        );
    }
}

