package com.swiftdrop.pricing.web;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.swiftdrop.pricing.application.PricingRuleAdministrationService.RuleValues;

/**
 * Create or update a pricing rule. Amounts use the same precision as the database column
 * NUMERIC(12,2); every field is required so a PUT always states the full rule.
 */
record PricingRuleRequest(
        @NotNull UUID originZoneId,
        @NotNull UUID destinationZoneId,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal baseFee,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal perKgFee,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal smallSizeSurcharge,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal mediumSizeSurcharge,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal largeSizeSurcharge,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotNull Boolean active) {

    RuleValues toValues() {
        return new RuleValues(originZoneId, destinationZoneId, baseFee, perKgFee,
                smallSizeSurcharge, mediumSizeSurcharge, largeSizeSurcharge, currency, active);
    }
}
