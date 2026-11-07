package com.swiftdrop.identity.api;

public record MerchantApprovalRequest(boolean approved, String rejectionReason) {}