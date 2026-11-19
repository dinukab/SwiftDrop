package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.merchant.domain.WebhookEndpoint;

public record WebhookEndpointResponse(
        UUID id,
        String url,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static WebhookEndpointResponse from(WebhookEndpoint endpoint) {
        return new WebhookEndpointResponse(
                endpoint.getId(),
                endpoint.getUrl(),
                endpoint.isActive(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}

