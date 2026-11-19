package com.swiftdrop.merchant.web;

import jakarta.validation.constraints.Size;

/**
 * Optional body of a pause, resume or cancel request.
 *
 * @param reason free text for the merchant's own records; stored in the order history only and never
 *        sent in events or webhooks
 */
public record OrderActionRequest(@Size(max = 500) String reason) {

    static final OrderActionRequest NONE = new OrderActionRequest(null);

    /** Never includes the reason. */
    @Override
    public String toString() {
        return "OrderActionRequest[]";
    }
}
