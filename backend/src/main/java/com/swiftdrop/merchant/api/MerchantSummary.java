package com.swiftdrop.merchant.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Public, read-only summary of a merchant for other modules (ADR 0003).
 */
public record MerchantSummary(
        UUID id,
        UUID userId,
        String businessName,
        String status
) {
    public MerchantSummary {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(businessName, "businessName must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}

