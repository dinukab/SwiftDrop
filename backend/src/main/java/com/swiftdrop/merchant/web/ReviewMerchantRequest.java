package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewMerchantRequest(
        @NotBlank
        @Pattern(regexp = "^(APPROVE|REJECT|SUSPEND)$", message = "Action must be APPROVE, REJECT, or SUSPEND")
        String action,

        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason
) {
    public ReviewMerchantRequest {
        action = action != null ? action.trim().toUpperCase() : null;
        reason = reason != null ? reason.trim() : null;
    }
}

