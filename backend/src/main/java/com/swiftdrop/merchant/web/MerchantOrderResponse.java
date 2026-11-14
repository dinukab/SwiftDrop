package com.swiftdrop.merchant.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.api.CreatedParcel;

public record MerchantOrderResponse(
        UUID parcelId,
        String trackingNumber,
        String status,
        BigDecimal feeAmount,
        String feeCurrency,
        Instant createdAt,
        boolean newlyCreated
) {
    public static MerchantOrderResponse from(CreatedParcel parcel) {
        return new MerchantOrderResponse(
                parcel.parcelId(),
                parcel.trackingNumber(),
                parcel.status().name(),
                parcel.feeAmount(),
                parcel.feeCurrency(),
                parcel.createdAt(),
                parcel.newlyCreated()
        );
    }
}

