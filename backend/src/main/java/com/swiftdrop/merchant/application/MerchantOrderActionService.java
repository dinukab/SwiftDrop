package com.swiftdrop.merchant.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.MerchantParcelControl;
import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Lets an approved merchant hold, release or cancel its own order before pickup (MER-4).
 *
 * <p>The caller has already checked that the merchant is approved. Ownership and the lifecycle rules
 * are enforced by {@code delivery} through {@link MerchantParcelControl} (ADR 0003, ADR 0006 §1A), so
 * another merchant's order is "not found" and an order past pickup is a conflict.
 */
@Service
@Lazy
public class MerchantOrderActionService {

    private final MerchantParcelControl control;

    public MerchantOrderActionService(MerchantParcelControl control) {
        this.control = control;
    }

    public ParcelStatus pause(UUID merchantId, UUID merchantUserId, String orderReference, String reason) {
        requireIds(merchantId, merchantUserId, orderReference);
        return control.pause(merchantId, orderReference, merchantUserId, reason);
    }

    public ParcelStatus resume(UUID merchantId, UUID merchantUserId, String orderReference, String reason) {
        requireIds(merchantId, merchantUserId, orderReference);
        return control.resume(merchantId, orderReference, merchantUserId, reason);
    }

    public ParcelStatus cancel(UUID merchantId, UUID merchantUserId, String orderReference, String reason) {
        requireIds(merchantId, merchantUserId, orderReference);
        return control.cancel(merchantId, orderReference, merchantUserId, reason);
    }

    private static void requireIds(UUID merchantId, UUID merchantUserId, String orderReference) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(merchantUserId, "merchantUserId");
        Objects.requireNonNull(orderReference, "orderReference");
    }
}
