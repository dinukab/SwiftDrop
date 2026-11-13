package com.swiftdrop.merchant.application;

/** Raised when a MERCHANT principal attempts a capability that requires APPROVED status (D-22). */
public class MerchantNotApprovedException extends RuntimeException {

    public MerchantNotApprovedException(String message) {
        super(message);
    }
}
