package com.swiftdrop.notification.infrastructure.messaging;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.swiftdrop.notification.application.NotificationService;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a {@code parcel.status-changed} event into a stored mock SMS for the parcel's customer (ADR 0004 section 8).
 * Redelivery of an event creates nothing new. Which statuses and parcels notify is decided by
 * {@code NotificationPolicy}; every other event is acknowledged and ignored, so it never blocks the queue.
 *
 * <p>A malformed event is rejected without requeue and goes to the dead-letter queue. Off unless
 * {@code swiftdrop.notifications.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.notifications.enabled", havingValue = "true")
public class ParcelStatusNotificationConsumer {

    static final String QUEUE = "notification.parcel-status-changed";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final NotificationService notifications;

    public ParcelStatusNotificationConsumer(NotificationService notifications) {
        this.notifications = notifications;
    }

    @RabbitListener(queues = QUEUE)
    public void onParcelStatusChanged(String json) {
        Envelope event;
        try {
            event = MAPPER.readValue(json, Envelope.class);
        } catch (RuntimeException e) {
            // Not a status event we understand: dead-letter it, do not retry it forever
            throw new AmqpRejectAndDontRequeueException("Malformed parcel status event");
        }
        if (event == null || event.eventId() == null || event.data() == null || event.data().parcelId() == null) {
            throw new AmqpRejectAndDontRequeueException("Malformed parcel status event");
        }
        Data data = event.data();
        notifications.record(event.eventId(), data.parcelId(), data.trackingNumber(), data.newStatus(), data.source(),
                data.ownerCustomerId());
    }

    /** The parts of the event this consumer needs; everything else is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(UUID eventId, Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(UUID parcelId, String trackingNumber, String newStatus, String source, UUID ownerCustomerId) {
    }
}
