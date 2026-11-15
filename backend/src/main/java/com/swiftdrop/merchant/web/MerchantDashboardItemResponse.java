package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;

/**
 * One order in the merchant portal.
 *
 * @param allowedActions what the merchant may do with the order right now ({@code PAUSE},
 *        {@code RESUME}, {@code CANCEL}), so the portal never has to guess the lifecycle rules
 */
public record MerchantDashboardItemResponse(
        UUID id,
        String trackingNumber,
        String status,
        String size,
        int weightGrams,
        String merchantOrderReference,
        Instant createdAt,
        List<String> allowedActions
) {
    public static MerchantDashboardItemResponse from(ParcelSummary summary) {
        return new MerchantDashboardItemResponse(
                summary.id(),
                summary.trackingNumber(),
                summary.status().name(),
                summary.size().name(),
                summary.weightGrams(),
                summary.merchantOrderReference(),
                summary.createdAt(),
                allowedActionsFor(summary.status())
        );
    }

    /** Hold or cancel before pickup; release a hold. Nothing once the courier has the parcel. */
    static List<String> allowedActionsFor(ParcelStatus status) {
        return switch (status) {
            case CREATED -> List.of("PAUSE", "CANCEL");
            case PAUSED -> List.of("RESUME", "CANCEL");
            default -> List.of();
        };
    }
}
