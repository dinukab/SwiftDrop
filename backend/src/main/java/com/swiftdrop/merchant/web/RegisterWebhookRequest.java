package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterWebhookRequest(
        @NotBlank(message = "Webhook URL is required")
        @Size(max = 2000, message = "URL cannot exceed 2000 characters")
        String url,

        @NotBlank(message = "Secret is required")
        @Size(min = 16, max = 128, message = "Secret must be between 16 and 128 characters")
        String secret
) {
    public RegisterWebhookRequest {
        url = url != null ? url.trim() : null;
        secret = secret != null ? secret.trim() : null;
    }

    /** Never includes the webhook secret in toString. */
    @Override
    public String toString() {
        return "RegisterWebhookRequest[url=" + url + ", secret=***]";
    }
}

