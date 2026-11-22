package com.swiftdrop.merchant.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.swiftdrop.merchant.domain.MerchantPickupAddress;

/**
 * The merchant's pickup (warehouse) address in a sign-up or update request. Same limits as an order address
 * ({@link MerchantOrderAddress}); {@code zoneId} is one of the active zones from {@code GET /api/v1/zones}.
 */
public record MerchantPickupAddressRequest(
        @NotBlank @Size(max = 200) String contactName,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$", message = "must be in international format, for example +94771234567")
        String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 10) String postalCode,
        @NotNull UUID zoneId
) {
    public MerchantPickupAddressRequest {
        contactName = contactName != null ? contactName.trim() : null;
        phone = phone != null ? phone.trim() : null;
        line1 = line1 != null ? line1.trim() : null;
        line2 = line2 != null ? line2.trim() : null;
        city = city != null ? city.trim() : null;
        postalCode = postalCode != null ? postalCode.trim() : null;
    }

    MerchantPickupAddress toDomain() {
        return new MerchantPickupAddress(contactName, phone, line1, line2, city, postalCode, zoneId);
    }

    /** Never includes the address: it is personal data. */
    @Override
    public String toString() {
        return "MerchantPickupAddressRequest[zoneId=" + zoneId + ", ***]";
    }
}
