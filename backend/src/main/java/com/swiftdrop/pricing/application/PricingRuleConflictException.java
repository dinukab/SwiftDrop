package com.swiftdrop.pricing.application;

/** Another rule already covers this origin → destination pair (HTTP 409). */
public class PricingRuleConflictException extends RuntimeException {

    public PricingRuleConflictException() {
        super("A pricing rule for this origin and destination already exists");
    }
}
