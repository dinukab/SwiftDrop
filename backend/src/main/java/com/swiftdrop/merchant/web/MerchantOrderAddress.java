package com.swiftdrop.merchant.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.swiftdrop.delivery.api.DeliveryContact;

public record MerchantOrderAddress(
        @NotBlank @Size(max = 200) String contactName,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @NotNull UUID zoneId,
        @Size(max = 10) String postalCode
) {
    public DeliveryContact toDeliveryContact() {
        return new DeliveryContact(
                contactName.trim(),
                phone.trim(),
                line1.trim(),
                line2 != null ? line2.trim() : null,
                city.trim(),
                zoneId,
                postalCode != null ? postalCode.trim() : null
        );
    }
}

