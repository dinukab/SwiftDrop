package com.swiftdrop.notification.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is notified about which status (ADR 0004 section 8). Only the customer who owns a customer-created parcel is
 * notified, and only for the statuses a person cares about; {@code CREATED} and {@code IN_TRANSIT} (and hub moves)
 * are skipped to limit noise. A merchant parcel never notifies: the merchant gets webhooks for every change. The text
 * names the tracking number and the status only.
 */
public final class NotificationPolicy {

    static final String CUSTOMER_SOURCE = "CUSTOMER";

    private static final Map<String, String> TEXT = Map.of(
            "PICKED_UP", "Your parcel %s has been picked up.",
            "OUT_FOR_DELIVERY", "Your parcel %s is out for delivery.",
            "DELIVERED", "Your parcel %s was delivered.",
            "FAILED", "Delivery of your parcel %s did not succeed. Please contact support.");

    private NotificationPolicy() {
    }

    /**
     * The message for one status change, or empty if nobody is notified.
     *
     * @param status the new status
     * @param source who created the parcel: {@code CUSTOMER} or {@code MERCHANT}
     * @param ownerCustomerId the customer who owns the parcel; null for a merchant parcel
     */
    public static Optional<String> messageFor(String status, String source, UUID ownerCustomerId,
            String trackingNumber) {
        if (ownerCustomerId == null || !CUSTOMER_SOURCE.equals(source) || status == null || trackingNumber == null) {
            return Optional.empty();
        }
        String template = TEXT.get(status);
        return template == null ? Optional.empty() : Optional.of(template.formatted(trackingNumber));
    }
}
