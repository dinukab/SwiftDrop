package com.swiftdrop.notification.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.notification.domain.Notification;

/** One message of the caller's inbox. {@code readAt} is null while it is unread. */
record NotificationResponse(
        UUID id,
        UUID parcelId,
        String trackingNumber,
        String status,
        String message,
        Instant createdAt,
        Instant readAt,
        boolean read) {

    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getParcelId(),
                notification.getTrackingNumber(), notification.getStatus(), notification.getMessage(),
                notification.getCreatedAt(), notification.getReadAt(), notification.isRead());
    }
}
