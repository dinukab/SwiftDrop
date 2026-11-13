package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public merchant registration request. Creates a MERCHANT user and PENDING merchant (D-22). */
public record RegisterMerchantRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 200) String businessName,
        @Size(max = 20) String contactPhone) {

    public RegisterMerchantRequest {
        fullName = fullName == null ? null : fullName.trim();
        email = email == null ? null : email.trim();
        businessName = businessName == null ? null : businessName.trim();
        contactPhone = contactPhone == null ? null : contactPhone.trim();
    }

    /** Never includes personal data or the password. */
    @Override
    public String toString() {
        return "RegisterMerchantRequest[***]";
    }
}
