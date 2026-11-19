package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin rejection of a pending merchant application. */
public record RejectMerchantRequest(
        @NotBlank @Size(max = 500) String reason) {

    public RejectMerchantRequest {
        reason = reason == null ? null : reason.trim();
    }
}
