package com.swiftdrop.merchant.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;

class ParcelStatusConsumerTest {

    private static final UUID MERCHANT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARCEL = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private WebhookEndpointRepository endpoints;
    private WebhookDeliveryRepository deliveries;
    private ParcelStatusConsumer consumer;

    @BeforeEach
    void setUp() {
        endpoints = mock(WebhookEndpointRepository.class);
        deliveries = mock(WebhookDeliveryRepository.class);
        consumer = new ParcelStatusConsumer(endpoints, deliveries);
    }

    @Test
    void aMerchantEventIsQueuedOncePerActiveEndpointWithTheExactBody() {
        WebhookEndpoint first = endpoint();
        WebhookEndpoint second = endpoint();
        when(endpoints.findByMerchantIdAndActiveTrue(MERCHANT)).thenReturn(List.of(first, second));
        when(deliveries.findByEventIdAndEndpointId(EVENT, first.getId())).thenReturn(Optional.empty());
        when(deliveries.findByEventIdAndEndpointId(EVENT, second.getId())).thenReturn(Optional.empty());

        consumer.onParcelStatusChanged(event(MERCHANT));

        ArgumentCaptor<WebhookDelivery> saved = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveries, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(delivery -> {
            assertThat(delivery.getEventId()).isEqualTo(EVENT);
            assertThat(delivery.getParcelId()).isEqualTo(PARCEL);
            assertThat(delivery.getMerchantId()).isEqualTo(MERCHANT);
            assertThat(delivery.getPayload()).isEqualTo(event(MERCHANT));
            assertThat(delivery.getStatus()).isEqualTo("PENDING");
        });
    }

    @Test
    void aRedeliveredEventCreatesNothingNew() {
        WebhookEndpoint endpoint = endpoint();
        when(endpoints.findByMerchantIdAndActiveTrue(MERCHANT)).thenReturn(List.of(endpoint));
        when(deliveries.findByEventIdAndEndpointId(EVENT, endpoint.getId()))
                .thenReturn(Optional.of(new WebhookDelivery(endpoint.getId(), MERCHANT, PARCEL, EVENT, "{}")));

        consumer.onParcelStatusChanged(event(MERCHANT));

        verify(deliveries, never()).save(any());
    }

    @Test
    void aCustomersParcelHasNoMerchantWebhook() {
        consumer.onParcelStatusChanged(event(null));

        verify(endpoints, never()).findByMerchantIdAndActiveTrue(any());
        verify(deliveries, never()).save(any());
    }

    @Test
    void aMerchantWithNoActiveEndpointGetsNothing() {
        when(endpoints.findByMerchantIdAndActiveTrue(MERCHANT)).thenReturn(List.of());

        consumer.onParcelStatusChanged(event(MERCHANT));

        verify(deliveries, never()).save(any());
    }

    @Test
    void aMalformedEventIsRejectedWithoutRequeue() {
        assertThatThrownBy(() -> consumer.onParcelStatusChanged("not json"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> consumer.onParcelStatusChanged("{\"eventId\":\"" + EVENT + "\"}"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void unknownFieldsAreIgnored() {
        WebhookEndpoint endpoint = endpoint();
        when(endpoints.findByMerchantIdAndActiveTrue(MERCHANT)).thenReturn(List.of(endpoint));
        when(deliveries.findByEventIdAndEndpointId(EVENT, endpoint.getId())).thenReturn(Optional.empty());

        consumer.onParcelStatusChanged(event(MERCHANT).replace("\"data\":{", "\"data\":{\"extra\":1,"));

        verify(deliveries).save(any(WebhookDelivery.class));
    }

    private WebhookEndpoint endpoint() {
        WebhookEndpoint endpoint = new WebhookEndpoint(MERCHANT, "https://api.acme.com/hooks", "encrypted");
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        return endpoint;
    }

    private static String event(UUID owner) {
        String ownerJson = owner == null ? "null" : "\"" + owner + "\"";
        return "{\"eventId\":\"" + EVENT + "\",\"eventType\":\"parcel.status-changed\",\"schemaVersion\":1,"
                + "\"occurredAt\":\"2026-11-15T10:00:00Z\",\"producer\":\"delivery\","
                + "\"data\":{\"parcelId\":\"" + PARCEL + "\",\"trackingNumber\":\"SD1234567890\","
                + "\"newStatus\":\"PAUSED\",\"sequence\":2,\"source\":\"MERCHANT\","
                + "\"ownerMerchantId\":" + ownerJson + "}}";
    }
}
