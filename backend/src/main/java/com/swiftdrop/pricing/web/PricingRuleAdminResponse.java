package com.swiftdrop.pricing.web;

import java.util.UUID;

import com.swiftdrop.pricing.domain.PricingRule;

/** A pricing rule as an admin sees it. Amounts are strings so they are never rounded as doubles. */
record PricingRuleAdminResponse(
        UUID id,
        UUID originZoneId,
        UUID destinationZoneId,
        String baseFee,
        String perKgFee,
        String smallSizeSurcharge,
        String mediumSizeSurcharge,
        String largeSizeSurcharge,
        String currency,
        boolean active,
        long version) {

    static PricingRuleAdminResponse from(PricingRule rule) {
        return new PricingRuleAdminResponse(
                rule.getId(),
                rule.getOriginZoneId(),
                rule.getDestinationZoneId(),
                rule.getBaseFee().toPlainString(),
                rule.getPerKgFee().toPlainString(),
                rule.getSmallSizeSurcharge().toPlainString(),
                rule.getMediumSizeSurcharge().toPlainString(),
                rule.getLargeSizeSurcharge().toPlainString(),
                rule.getCurrency(),
                rule.isActive(),
                rule.getVersion());
    }
}
