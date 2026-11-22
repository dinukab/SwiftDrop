package com.swiftdrop.delivery.api;

import java.util.UUID;

/**
 * Lets a merchant hold, release or cancel its own delivery before pickup (ADR 0006 amendment).
 * {@code merchant} has already checked that the caller is an approved merchant; {@code delivery}
 * enforces ownership and the lifecycle rules, so no other merchant's order can be touched.
 *
 * <p>Each call is a status change: it takes the next sequence number, is written to the status
 * history with the acting user, and emits a {@code parcel.status-changed} event.
 *
 * <p>All three methods throw {@link ParcelNotFoundException} if the merchant has no order with that
 * reference, and {@link ParcelConflictException} if the order is not in a status that allows the move.
 * {@link IllegalArgumentException} is thrown for a {@code reason} longer than 500 characters.
 */
public interface MerchantParcelControl {

    /** Holds the order. Allowed only while it is {@link ParcelStatus#CREATED}. Returns the new status. */
    ParcelStatus pause(UUID merchantId, String merchantOrderReference, UUID merchantUserId, String reason);

    /** Releases a held order back to {@link ParcelStatus#CREATED}. Returns the new status. */
    ParcelStatus resume(UUID merchantId, String merchantOrderReference, UUID merchantUserId, String reason);

    /**
     * Cancels the order. Allowed while it is {@link ParcelStatus#CREATED} or {@link ParcelStatus#PAUSED};
     * final. Returns the new status.
     */
    ParcelStatus cancel(UUID merchantId, String merchantOrderReference, UUID merchantUserId, String reason);
}
