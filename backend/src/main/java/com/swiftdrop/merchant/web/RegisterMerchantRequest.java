package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.swiftdrop.merchant.application.MerchantRegistrationCommand;

public record RegisterMerchantRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(max = 50) String registrationNumber,
        @NotBlank @Email @Size(max = 254) String contactEmail,
        @NotBlank @Size(max = 30) String contactPhone,
        @NotBlank @Size(min = 8, max = 72) String password
) {
    public RegisterMerchantRequest {
        fullName = fullName != null ? fullName.trim() : null;
        businessName = businessName != null ? businessName.trim() : null;
        registrationNumber = registrationNumber != null ? registrationNumber.trim() : null;
        contactEmail = contactEmail != null ? contactEmail.trim() : null;
        contactPhone = contactPhone != null ? contactPhone.trim() : null;
    }

    public MerchantRegistrationCommand toCommand() {
        return new MerchantRegistrationCommand(
                fullName,
                businessName,
                registrationNumber,
                contactEmail,
                contactPhone,
                password
        );
    }

    /** Never includes sensitive credentials in logs or strings. */
    @Override
    public String toString() {
        return "RegisterMerchantRequest[businessName=" + businessName + ", registrationNumber=" + registrationNumber + ", ***]";
    }
}

