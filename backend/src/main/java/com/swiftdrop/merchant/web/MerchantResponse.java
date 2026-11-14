package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.domain.Merchant;

/** REST representation of a merchant profile. */
public record MerchantResponse(
        UUID id,
        UUID userId,
        String businessName,
        String contactPhone,
        String status,
        String rejectionReason,
        Instant createdAt,
        Instant reviewedAt,
        UUID reviewedBy) {

    static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getUserId(),
                merchant.getBusinessName(),
                merchant.getContactPhone(),
                merchant.getStatus().name(),
                merchant.getRejectionReason(),
                merchant.getCreatedAt(),
                merchant.getReviewedAt(),
                merchant.getReviewedBy());
    }
}
