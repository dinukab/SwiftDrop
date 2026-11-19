package com.swiftdrop.merchant.application;

/** Raised when merchant registration conflicts with an existing account or merchant profile. */
public class MerchantConflictException extends RuntimeException {

    public MerchantConflictException(String message) {
        super(message);
    }
}
