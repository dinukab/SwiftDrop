package com.swiftdrop.pricing.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or update a delivery zone. Every field is required, so a PUT always states the full zone. */
record ZoneRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull Boolean active) {
}
