package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.domain.WebhookDelivery;

/**
 * One webhook delivery for the merchant's portal. Carries IDs, status and timings only. The event body
 * is not returned: it is the same event the merchant already receives, and it stays out of this view.
 */
public record WebhookDeliveryResponse(
        UUID id,
        UUID endpointId,
        UUID eventId,
        UUID parcelId,
        String status,
        int attemptCount,
        int manualRedeliveries,
        Integer lastHttpStatus,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        Instant createdAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery delivery) {
        return new WebhookDeliveryResponse(
                delivery.getId(),
                delivery.getEndpointId(),
                delivery.getEventId(),
                delivery.getParcelId(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getManualRedeliveries(),
                delivery.getLastHttpStatus(),
                delivery.getLastAttemptAt(),
                delivery.getNextAttemptAt(),
                delivery.getCreatedAt()
        );
    }
}
