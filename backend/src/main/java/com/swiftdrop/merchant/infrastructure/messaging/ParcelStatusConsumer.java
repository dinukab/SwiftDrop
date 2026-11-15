package com.swiftdrop.merchant.infrastructure.messaging;

import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a {@code parcel.status-changed} event for a merchant's order into one pending delivery per
 * active webhook endpoint (ADR 0004 section 2, 7.1). The event body is stored with the delivery, so
 * every attempt sends the same bytes. Redelivery of an event creates nothing new: one delivery per
 * event and endpoint.
 *
 * <p>Only {@code merchant.parcel-status-changed} is read here. A malformed event is rejected without
 * requeue and goes to the dead-letter queue. Off unless {@code swiftdrop.webhooks.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.webhooks.enabled", havingValue = "true")
public class ParcelStatusConsumer {

    static final String QUEUE = "merchant.parcel-status-changed";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final WebhookEndpointRepository endpoints;
    private final WebhookDeliveryRepository deliveries;

    public ParcelStatusConsumer(WebhookEndpointRepository endpoints, WebhookDeliveryRepository deliveries) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
    }

    @RabbitListener(queues = QUEUE)
    @Transactional
    public void onParcelStatusChanged(String json) {
        Envelope event;
        try {
            event = MAPPER.readValue(json, Envelope.class);
        } catch (RuntimeException e) {
            // Not a status event we understand: dead-letter it, do not retry it forever
            throw new AmqpRejectAndDontRequeueException("Malformed parcel status event");
        }
        if (event.eventId() == null || event.data() == null || event.data().parcelId() == null) {
            throw new AmqpRejectAndDontRequeueException("Malformed parcel status event");
        }
        UUID merchantId = event.data().ownerMerchantId();
        if (merchantId == null) {
            return; // a customer's parcel: no merchant webhook
        }
        for (var endpoint : endpoints.findByMerchantIdAndActiveTrue(merchantId)) {
            if (deliveries.findByEventIdAndEndpointId(event.eventId(), endpoint.getId()).isPresent()) {
                continue; // already queued (a redelivered message)
            }
            deliveries.save(new WebhookDelivery(endpoint.getId(), merchantId, event.data().parcelId(),
                    event.eventId(), json));
        }
    }

    /** The parts of the event this consumer needs; everything else is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(UUID eventId, Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(UUID parcelId, UUID ownerMerchantId) {
    }
}
