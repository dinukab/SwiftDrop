package com.swiftdrop.merchant.infrastructure.messaging;

import com.swiftdrop.delivery.api.ParcelStatusChanged;

/**
 * Stage 4 adapter: create one delivery per active endpoint, unique by event ID and endpoint ID,
 * in the caller's transaction. Do not send HTTP here. Return true only after recording the effect.
 */
@FunctionalInterface
public interface MerchantWebhookEffect {
    boolean createDeliveries(ParcelStatusChanged event);
}
