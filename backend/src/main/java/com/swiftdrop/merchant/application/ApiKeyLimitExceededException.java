package com.swiftdrop.merchant.application;

import java.util.UUID;

public class ApiKeyLimitExceededException extends RuntimeException {

    public ApiKeyLimitExceededException(UUID merchantId, int maxKeys) {
        super("Merchant " + merchantId + " has reached the maximum of " + maxKeys + " active API keys");
    }
}

