package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
        @NotBlank(message = "Label is required")
        @Size(max = 100, message = "Label must not exceed 100 characters")
        String label
) {
    public CreateApiKeyRequest {
        label = label != null ? label.trim() : null;
    }
}

