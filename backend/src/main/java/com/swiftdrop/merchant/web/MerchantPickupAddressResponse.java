package com.swiftdrop.merchant.web;

import java.util.UUID;

import com.swiftdrop.merchant.domain.MerchantPickupAddress;

/** The merchant's pickup address, shown to the merchant and to admins; {@code null} when none is registered. */
public record MerchantPickupAddressResponse(
        String contactName,
        String phone,
        String line1,
        String line2,
        String city,
        String postalCode,
        UUID zoneId
) {
    static MerchantPickupAddressResponse from(MerchantPickupAddress address) {
        if (address == null) {
            return null;
        }
        return new MerchantPickupAddressResponse(address.getContactName(), address.getPhone(), address.getLine1(),
                address.getLine2(), address.getCity(), address.getPostalCode(), address.getZoneId());
    }
}
