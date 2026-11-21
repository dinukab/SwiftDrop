package com.swiftdrop.merchant.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMerchantOrderRequest(
        @NotBlank @Size(max = 100) String merchantOrderReference,
        @NotNull @Valid MerchantOrderAddress pickup,
        @NotNull @Valid MerchantOrderAddress dropoff,
        @NotBlank @Pattern(regexp = "^(SMALL|MEDIUM|LARGE)$", message = "Size must be SMALL, MEDIUM, or LARGE")
        String size,
        @Min(1) @Max(30000) int weightGrams,
        @Size(max = 200) String description
) {
    public CreateMerchantOrderRequest {
        merchantOrderReference = merchantOrderReference != null ? merchantOrderReference.trim() : null;
        size = size != null ? size.trim().toUpperCase() : null;
        description = description != null ? description.trim() : null;
    }
}

