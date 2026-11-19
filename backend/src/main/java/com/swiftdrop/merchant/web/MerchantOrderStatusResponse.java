package com.swiftdrop.merchant.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.merchant.application.MerchantOrderStatusService.OrderStatus;

/**
 * Status of one order for the merchant's e-commerce site: where it is now and every status change
 * so far. Holds IDs, codes and times only; no recipient name, phone or address.
 *
 * @param lastSequence sequence number of the newest change; apply a change only if its sequence is
 *        higher than the last one you applied (a held order can return to {@code CREATED}, so the
 *        status alone does not tell old from new)
 */
public record MerchantOrderStatusResponse(
        UUID parcelId,
        String trackingNumber,
        String merchantOrderReference,
        String status,
        int lastSequence,
        Instant createdAt,
        List<Event> events
) {

    /**
     * One status change.
     *
     * @param previousStatus {@code null} for the first change, {@code CREATED}
     * @param failureReasonCode set only when {@code status} is {@code FAILED}
     */
    public record Event(
            int sequence,
            UUID eventId,
            String status,
            String previousStatus,
            Instant occurredAt,
            String failureReasonCode
    ) {
        static Event from(ParcelStatusChanged event) {
            ParcelStatusChangedData data = event.data();
            return new Event(
                    data.sequence(),
                    event.eventId(),
                    data.newStatus().name(),
                    data.previousStatus() != null ? data.previousStatus().name() : null,
                    event.occurredAt(),
                    data.failureReasonCode() != null ? data.failureReasonCode().name() : null
            );
        }
    }

    public static MerchantOrderStatusResponse from(OrderStatus order) {
        List<Event> events = order.events().stream().map(Event::from).toList();
        int lastSequence = events.isEmpty() ? 0 : events.getLast().sequence();
        return new MerchantOrderStatusResponse(
                order.parcel().id(),
                order.parcel().trackingNumber(),
                order.parcel().merchantOrderReference(),
                order.parcel().status().name(),
                lastSequence,
                order.parcel().createdAt(),
                events
        );
    }
}
