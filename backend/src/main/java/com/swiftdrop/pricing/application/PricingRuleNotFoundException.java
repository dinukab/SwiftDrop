package com.swiftdrop.pricing.application;

import java.util.UUID;

/** No pricing rule has the requested id (HTTP 404). */
public class PricingRuleNotFoundException extends RuntimeException {

    public PricingRuleNotFoundException(UUID id) {
        super("Pricing rule " + id + " not found");
    }
}
