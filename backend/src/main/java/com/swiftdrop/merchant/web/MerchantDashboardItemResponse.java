package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelSummary;

public record MerchantDashboardItemResponse(
        UUID id,
        String trackingNumber,
        String status,
        String size,
        int weightGrams,
        String merchantOrderReference,
        Instant createdAt
) {
    public static MerchantDashboardItemResponse from(ParcelSummary summary) {
        return new MerchantDashboardItemResponse(
                summary.id(),
                summary.trackingNumber(),
                summary.status().name(),
                summary.size().name(),
                summary.weightGrams(),
                summary.merchantOrderReference(),
                summary.createdAt()
        );
    }
}

