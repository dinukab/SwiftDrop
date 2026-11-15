package com.swiftdrop.notification.infrastructure.messaging;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Set;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.delivery.api.ParcelEvents;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.shared.messaging.ListenerAttempts;
import com.swiftdrop.shared.messaging.OrderedInbox;

public final class NotificationStatusConsumer {
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final OrderedInbox inbox;

    public NotificationStatusConsumer(JdbcTemplate jdbc, TransactionTemplate transactions,
            ParcelEvents history, MeterRegistry meters) {
        inbox = new OrderedInbox(jdbc, transactions, "notification_event",
                (id, from, to) -> history.eventsBetween(id, from, to).stream()
                        .map(this::envelope).toList(),
                pending -> {
                    ParcelStatusChanged event = decode(pending.payload());
                    if (Set.of(ParcelStatus.PICKED_UP, ParcelStatus.OUT_FOR_DELIVERY,
                            ParcelStatus.DELIVERED, ParcelStatus.FAILED).contains(event.data().newStatus())) {
                        jdbc.update("""
                                INSERT INTO notification_messages
                                (event_id, recipient_user_id, parcel_id, tracking_number, status, created_at)
                                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                                """, event.eventId(), event.data().ownerCustomerId(), event.data().parcelId(),
                                event.data().trackingNumber(), event.data().newStatus().name(),
                                Timestamp.from(event.occurredAt()));
                    }
                    return true;
                }, Clock.systemUTC(), meters);
    }

    @RabbitListener(queues = "notification.parcel-status-changed",
            containerFactory = "reliableEventListenerFactory")
    public void receive(Message message) {
        ListenerAttempts.run(() -> {
            if (!"application/json".equals(message.getMessageProperties().getContentType())) {
                throw new IllegalArgumentException("Unsupported content type");
            }
            ParcelStatusChanged event = decode(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8));
            if (!event.eventId().toString().equals(message.getMessageProperties().getMessageId())
                    || !event.eventType().equals(message.getMessageProperties().getType())) {
                throw new IllegalArgumentException("Invalid message identity");
            }
            if (event.data().source() == ParcelSource.CUSTOMER) {
                inbox.accept(envelope(event));
            }
        });
    }

    @Scheduled(fixedDelayString = "${swiftdrop.messaging.reconciliation-poll-ms:5000}")
    public void reconcile() {
        try {
            inbox.reconcile();
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(NotificationStatusConsumer.class)
                    .error("Notification reconciliation unavailable; pending events retained");
        }
    }

    private OrderedInbox.Event envelope(ParcelStatusChanged event) {
        return new OrderedInbox.Event(event.eventId(), event.data().parcelId(), event.data().sequence(),
                mapper.writeValueAsString(event));
    }

    private ParcelStatusChanged decode(String payload) {
        ParcelStatusChanged event = mapper.readValue(payload, ParcelStatusChanged.class);
        var data = event.data();
        if (!ParcelStatusChanged.EVENT_TYPE.equals(event.eventType()) || event.schemaVersion() != 1
                || !ParcelStatusChanged.PRODUCER.equals(event.producer())
                || data.trackingNumber().isBlank()
                || (data.source() == ParcelSource.CUSTOMER
                    ? data.ownerCustomerId() == null || data.ownerMerchantId() != null
                    : data.ownerMerchantId() == null || data.ownerCustomerId() != null)
                || (data.sequence() == 1
                    ? data.previousStatus() != null || data.newStatus() != ParcelStatus.CREATED
                    : data.previousStatus() == null || data.newStatus() == ParcelStatus.CREATED)) {
            throw new IllegalArgumentException("Unsupported or invalid status event");
        }
        return event;
    }
}
