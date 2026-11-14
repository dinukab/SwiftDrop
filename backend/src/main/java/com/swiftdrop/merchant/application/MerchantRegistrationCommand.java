package com.swiftdrop.merchant.application;

import java.util.Objects;

public record MerchantRegistrationCommand(
        String fullName,
        String businessName,
        String registrationNumber,
        String contactEmail,
        String contactPhone,
        String password
) {
    public MerchantRegistrationCommand {
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(businessName, "businessName");
        Objects.requireNonNull(registrationNumber, "registrationNumber");
        Objects.requireNonNull(contactEmail, "contactEmail");
        Objects.requireNonNull(contactPhone, "contactPhone");
        Objects.requireNonNull(password, "password");
    }

    /** Never includes the raw password. */
    @Override
    public String toString() {
        return "MerchantRegistrationCommand[businessName=" + businessName
                + ", registrationNumber=" + registrationNumber + ", contactEmail=" + contactEmail + ", ***]";
    }
}

