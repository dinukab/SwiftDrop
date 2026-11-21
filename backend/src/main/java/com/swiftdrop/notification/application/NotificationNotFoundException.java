package com.swiftdrop.notification.application;

/** The notification does not exist or belongs to someone else; the two look the same on purpose. */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Notification not found");
    }
}
