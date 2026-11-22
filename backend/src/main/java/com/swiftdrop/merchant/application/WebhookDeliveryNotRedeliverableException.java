package com.swiftdrop.merchant.application;

/**
 * A delivery cannot be sent again: it has not failed, its endpoint was removed, or its manual
 * redelivery limit is used up. The message names the rule only.
 */
public class WebhookDeliveryNotRedeliverableException extends RuntimeException {

    public WebhookDeliveryNotRedeliverableException(String message) {
        super(message);
    }
}
