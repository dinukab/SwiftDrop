package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.domain.Merchant;

public record MerchantResponse(
        UUID id,
        UUID userId,
        String businessName,
        String registrationNumber,
        String contactEmail,
        String contactPhone,
        String status,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static MerchantResponse from(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getUserId(),
                merchant.getBusinessName(),
                merchant.getRegistrationNumber(),
                merchant.getContactEmail(),
                merchant.getContactPhone(),
                merchant.getStatus().name(),
                merchant.getReviewedBy(),
                merchant.getReviewedAt(),
                merchant.getRejectionReason(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt()
        );
    }
}

