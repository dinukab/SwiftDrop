package com.swiftdrop.merchant.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelSummary;

/**
 * Current status and full sequenced history of one order, for the merchant's e-commerce site (MER-2).
 *
 * <p>Always scoped by the merchant ID, so an API key can read only its own merchant's orders; another
 * merchant's reference is simply "not found". The history comes from {@link ParcelEvents}: the same
 * events a webhook carries, so the site can apply polled and pushed news the same way. They hold IDs
 * and codes only, never names, phones or addresses.
 */
@Service
@Lazy
public class MerchantOrderStatusService {

    private final ParcelQueries parcelQueries;
    private final ParcelEvents parcelEvents;

    public MerchantOrderStatusService(ParcelQueries parcelQueries, ParcelEvents parcelEvents) {
        this.parcelQueries = parcelQueries;
        this.parcelEvents = parcelEvents;
    }

    /** The order and its events in sequence order; empty if this merchant has no such order. */
    public Optional<OrderStatus> find(UUID merchantId, String merchantOrderReference) {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(merchantOrderReference, "merchantOrderReference");

        return parcelQueries.findByMerchantReference(merchantId, merchantOrderReference.trim())
                .map(parcel -> new OrderStatus(parcel, parcelEvents.eventsBetween(parcel.id(), 1, Integer.MAX_VALUE)));
    }

    public record OrderStatus(ParcelSummary parcel, List<ParcelStatusChanged> events) {

        public OrderStatus {
            events = List.copyOf(events);
        }
    }
}
