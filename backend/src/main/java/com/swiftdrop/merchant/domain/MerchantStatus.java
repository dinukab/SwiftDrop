package com.swiftdrop.merchant.domain;

/** Lifecycle status of a registered merchant (D-22 / SD-401). */
public enum MerchantStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}
