package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;

class WebhookDeliveryHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final UUID MERCHANT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private WebhookDeliveryRepository deliveries;
    private WebhookEndpointRepository endpoints;
    private WebhookDeliveryHistoryService service;
    private WebhookEndpoint endpoint;
    private WebhookDelivery delivery;

    @BeforeEach
    void setUp() {
        deliveries = mock(WebhookDeliveryRepository.class);
        endpoints = mock(WebhookEndpointRepository.class);
        service = new WebhookDeliveryHistoryService(deliveries, endpoints, Clock.fixed(NOW, ZoneOffset.UTC));

        endpoint = new WebhookEndpoint(MERCHANT, "https://api.acme.com/hooks", "encrypted");
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        delivery = new WebhookDelivery(endpoint.getId(), MERCHANT, UUID.randomUUID(), UUID.randomUUID(), "{}");
        // The id is generated when the row is saved, so a unit test sets it like the endpoint's above.
        ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
        delivery.recordFailure(404);
        when(deliveries.findByIdAndMerchantId(delivery.getId(), MERCHANT)).thenReturn(Optional.of(delivery));
        when(deliveries.save(any(WebhookDelivery.class))).thenAnswer(call -> call.getArgument(0));
        when(endpoints.findById(endpoint.getId())).thenReturn(Optional.of(endpoint));
    }

    @Test
    void aFailedDeliveryOfAnActiveEndpointIsRedelivered() {
        WebhookDelivery result = service.redeliver(delivery.getId(), MERCHANT);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getManualRedeliveries()).isEqualTo(1);
        assertThat(result.getNextAttemptAt()).isEqualTo(NOW);
        verify(deliveries).save(delivery);
    }

    @Test
    void anotherMerchantsDeliveryLooksNotFound() {
        UUID other = UUID.randomUUID();
        when(deliveries.findByIdAndMerchantId(delivery.getId(), other)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeliver(delivery.getId(), other))
                .isInstanceOf(MerchantNotFoundException.class);
        verify(deliveries, never()).save(any());
    }

    @Test
    void aDeliveryThatDidNotFailIsRefused() {
        WebhookDelivery sent = new WebhookDelivery(endpoint.getId(), MERCHANT, UUID.randomUUID(), UUID.randomUUID(), "{}");
        ReflectionTestUtils.setField(sent, "id", UUID.randomUUID());
        sent.recordSuccess(200);
        when(deliveries.findByIdAndMerchantId(sent.getId(), MERCHANT)).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> service.redeliver(sent.getId(), MERCHANT))
                .isInstanceOf(WebhookDeliveryNotRedeliverableException.class)
                .hasMessage("Only a failed delivery can be sent again");
    }

    @Test
    void aDeliveryToADeactivatedEndpointIsRefused() {
        endpoint.deactivate();

        assertThatThrownBy(() -> service.redeliver(delivery.getId(), MERCHANT))
                .isInstanceOf(WebhookDeliveryNotRedeliverableException.class)
                .hasMessage("The webhook endpoint is no longer active");
        verify(deliveries, never()).save(any());
    }

    @Test
    void aDeliveryAtTheManualLimitIsRefused() {
        for (int i = 0; i < WebhookDelivery.MAX_MANUAL_REDELIVERIES; i++) {
            delivery.redeliver(NOW);
            delivery.recordFailure(404);
        }

        assertThatThrownBy(() -> service.redeliver(delivery.getId(), MERCHANT))
                .isInstanceOf(WebhookDeliveryNotRedeliverableException.class);
    }

    @Test
    void theHistoryIsScopedToTheMerchantAndCapsThePageSize() {
        service.history(MERCHANT, -3, 500);

        verify(deliveries).findByMerchantIdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(MERCHANT),
                org.mockito.ArgumentMatchers.argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 100));
    }
}
