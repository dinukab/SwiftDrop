package com.swiftdrop.merchant.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public facade of the merchant module for other modules (ADR 0003).
 */
public interface MerchantLookup {

    Optional<MerchantSummary> findById(UUID merchantId);

    Optional<MerchantSummary> findByUserId(UUID userId);

    boolean isApproved(UUID merchantId);
}

