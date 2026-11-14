package com.swiftdrop.merchant.application;

import java.util.UUID;

public class MerchantNotFoundException extends RuntimeException {

    public MerchantNotFoundException(UUID merchantId) {
        super("Merchant not found: " + merchantId);
    }

    public MerchantNotFoundException(String message) {
        super(message);
    }
}

