package com.swiftdrop.merchant.infrastructure.messaging;

import java.time.Clock;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
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

public final class MerchantStatusConsumer {
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final OrderedInbox inbox;

    public MerchantStatusConsumer(JdbcTemplate jdbc, TransactionTemplate transactions,
            ParcelEvents history, MeterRegistry meters, ObjectProvider<MerchantWebhookEffect> effects) {
        inbox = new OrderedInbox(jdbc, transactions, "merchant_event",
                (id, from, to) -> history.eventsBetween(id, from, to).stream()
                        .map(this::envelope).toList(),
                pending -> {
                    ParcelStatusChanged event = decode(pending.payload());
                    MerchantWebhookEffect effect = effects.getIfAvailable();
                    return effect != null && effect.createDeliveries(event);
                }, Clock.systemUTC(), meters);
    }

    @RabbitListener(queues = "merchant.parcel-status-changed",
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
            if (event.data().source() == ParcelSource.MERCHANT) {
                inbox.accept(envelope(event));
            }
        });
    }

    @Scheduled(fixedDelayString = "${swiftdrop.messaging.reconciliation-poll-ms:5000}")
    public void reconcile() {
        try {
            inbox.reconcile();
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(MerchantStatusConsumer.class)
                    .error("Merchant reconciliation unavailable; pending events retained");
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
