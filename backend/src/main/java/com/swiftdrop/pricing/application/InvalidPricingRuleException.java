package com.swiftdrop.pricing.application;

/** A rule's origin or destination zone is missing, so the rule cannot be saved (HTTP 400). */
public class InvalidPricingRuleException extends RuntimeException {

    public InvalidPricingRuleException(String message) {
        super(message);
    }
}
