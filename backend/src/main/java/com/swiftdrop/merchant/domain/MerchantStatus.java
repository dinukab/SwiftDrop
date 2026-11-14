package com.swiftdrop.merchant.domain;

/**
 * Status of a merchant registration and account lifecycle (MER-1, ADM-3).
 */
public enum MerchantStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUSPENDED
}

