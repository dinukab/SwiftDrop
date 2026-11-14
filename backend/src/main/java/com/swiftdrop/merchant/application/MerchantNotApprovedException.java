package com.swiftdrop.merchant.application;

import java.util.UUID;

public class MerchantNotApprovedException extends RuntimeException {

    public MerchantNotApprovedException(UUID merchantId) {
        super("Merchant account is not approved for API operations: " + merchantId);
    }
}

