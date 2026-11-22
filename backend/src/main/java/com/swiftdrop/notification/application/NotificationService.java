package com.swiftdrop.notification.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.notification.domain.Notification;
import com.swiftdrop.notification.domain.NotificationRepository;

/**
 * The mock SMS channel (ADR 0004 section 8): stores a message per qualifying status event and lets the recipient read
 * their own inbox. Every read and change is scoped to the calling user. Message text is never logged.
 */
@Service
@Lazy
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final Clock clock;

    @Autowired
    public NotificationService(NotificationRepository notifications) {
        this(notifications, Clock.systemUTC());
    }

    NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Stores the notification for one status event if the policy asks for one. Idempotent: an event that was seen
     * before (a redelivered message) creates nothing.
     *
     * @return {@code true} if a notification was stored
     */
    @Transactional
    public boolean record(UUID eventId, UUID parcelId, String trackingNumber, String status, String source,
            UUID ownerCustomerId) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(parcelId, "parcelId");
        var message = NotificationPolicy.messageFor(status, source, ownerCustomerId, trackingNumber);
        if (message.isEmpty()) {
            return false;
        }
        if (notifications.existsByEventId(eventId)) {
            return false;
        }
        notifications.save(new Notification(ownerCustomerId, parcelId, trackingNumber, eventId, status, message.get(),
                clock.instant()));
        // IDs only: never the text
        log.info("Notification stored for user {} (parcel {}, {})", ownerCustomerId, parcelId, status);
        return true;
    }

    /** The user's inbox, newest first. */
    @Transactional(readOnly = true)
    public Page<Notification> inbox(UUID userId, int page, int size) {
        Objects.requireNonNull(userId, "userId");
        return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    /**
     * Marks one of the user's notifications read. Reading it again changes nothing.
     *
     * @throws NotificationNotFoundException if it does not exist or is someone else's
     */
    @Transactional
    public Notification markRead(UUID userId, UUID notificationId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(notificationId, "notificationId");
        Notification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        notification.markRead(clock.instant());
        return notification;
    }

    /** Marks all of the user's unread notifications read; returns how many changed. */
    @Transactional
    public int markAllRead(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return notifications.markAllRead(userId, clock.instant());
    }
}
