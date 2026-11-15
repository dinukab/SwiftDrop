package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;

class WebhookDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final String SECRET = "endpoint-secret-12345";
    private static final String BODY = "{\"eventId\":\"e\"}";
    private static final UUID MERCHANT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private WebhookDeliveryRepository deliveries;
    private WebhookEndpointRepository endpoints;
    private WebhookSecretCipher cipher;
    private WebhookTransport transport;
    private WebhookEndpoint endpoint;
    private WebhookDeliveryService service;

    @BeforeEach
    void setUp() {
        deliveries = mock(WebhookDeliveryRepository.class);
        endpoints = mock(WebhookEndpointRepository.class);
        transport = mock(WebhookTransport.class);
        cipher = new WebhookSecretCipher("test=9YNCoF61LK2O35ShQ1pKG+Roty+tHmUplzzjcEjNz2M=", "test");
        endpoint = new WebhookEndpoint(MERCHANT, "https://api.acme.com/hooks",
                cipher.encrypt(SECRET, MERCHANT));
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        when(endpoints.findById(any())).thenReturn(Optional.of(endpoint));
        service = new WebhookDeliveryService(deliveries, endpoints, cipher, new WebhookUrlPolicy(false), transport,
                Clock.fixed(NOW, ZoneOffset.UTC), new Random(7));
    }

    @Test
    void aTwoHundredResponseMarksTheDeliveryDone() {
        WebhookDelivery delivery = pending();
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(delivery));
        when(transport.post(any(), anyMap(), anyString())).thenReturn(new WebhookTransport.Result(200));

        assertThat(service.deliverDue()).isEqualTo(1);

        assertThat(delivery.getStatus()).isEqualTo("SUCCESS");
        assertThat(delivery.getLastHttpStatus()).isEqualTo(200);
    }

    @Test
    void theRequestIsSignedOverTheStoredBodyWithTheEndpointSecret() {
        WebhookDelivery delivery = pending();
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(delivery));
        when(transport.post(any(), anyMap(), anyString())).thenReturn(new WebhookTransport.Result(204));

        service.deliverDue();

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(transport).post(uri.capture(), headers.capture(), eq(BODY));
        String timestamp = String.valueOf(NOW.getEpochSecond());
        assertThat(uri.getValue().toString()).isEqualTo("https://api.acme.com/hooks");
        assertThat(headers.getValue())
                .containsEntry("X-SwiftDrop-Event-Type", "parcel.status-changed")
                .containsEntry("X-SwiftDrop-Timestamp", timestamp)
                .containsEntry("X-SwiftDrop-Signature", WebhookSigner.sign(SECRET, timestamp, BODY))
                .containsEntry("X-SwiftDrop-Event-Id", delivery.getEventId().toString());
    }

    @Test
    void aServerErrorIsRetriedWithBackoff() {
        WebhookDelivery delivery = pending();
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(delivery));
        when(transport.post(any(), anyMap(), anyString())).thenReturn(new WebhookTransport.Result(503));

        service.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo("PENDING");
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getLastHttpStatus()).isEqualTo(503);
        // about 30 seconds after the first failure, within the 20 % jitter
        Duration wait = Duration.between(NOW, delivery.getNextAttemptAt());
        assertThat(wait).isBetween(Duration.ofSeconds(24), Duration.ofSeconds(36));
    }

    @Test
    void noResponseAndRateLimitingAreRetryable() {
        WebhookDelivery noResponse = pending();
        WebhookDelivery limited = pending();
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(noResponse, limited));
        when(transport.post(any(), anyMap(), anyString()))
                .thenReturn(new WebhookTransport.Result(null))
                .thenReturn(new WebhookTransport.Result(429));

        service.deliverDue();

        assertThat(noResponse.getStatus()).isEqualTo("PENDING");
        assertThat(limited.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void otherClientErrorsFailAtOnceWithoutRetry() {
        WebhookDelivery delivery = pending();
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(delivery));
        when(transport.post(any(), anyMap(), anyString())).thenReturn(new WebhookTransport.Result(404));

        service.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        assertThat(delivery.getLastHttpStatus()).isEqualTo(404);
    }

    @Test
    void theEighthFailedAttemptIsTheLast() {
        WebhookDelivery delivery = pending();
        for (int i = 0; i < WebhookDeliveryService.MAX_ATTEMPTS - 1; i++) {
            delivery.recordRetry(503, NOW);
        }
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(delivery));
        when(transport.post(any(), anyMap(), anyString())).thenReturn(new WebhookTransport.Result(503));

        service.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        assertThat(delivery.getAttemptCount()).isEqualTo(WebhookDeliveryService.MAX_ATTEMPTS);
    }

    @Test
    void aDeactivatedEndpointIsNeverSentTo() {
        endpoint.deactivate();
        WebhookDelivery delivery = pending();
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(delivery));

        service.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        verify(transport, never()).post(any(), anyMap(), anyString());
    }

    @Test
    void aRowWithoutAStoredBodyIsFailedNotGuessed() {
        WebhookDelivery delivery = new WebhookDelivery(endpoint.getId(), MERCHANT, UUID.randomUUID(), UUID.randomUUID());
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(delivery));

        service.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        verify(transport, never()).post(any(), anyMap(), anyString());
    }

    @Test
    void aSecretThatCannotBeReadFailsTheDeliveryWithoutSending() {
        endpoint = new WebhookEndpoint(MERCHANT, "https://api.acme.com/hooks",
                "legacy-base64-value");
        ReflectionTestUtils.setField(endpoint, "id", UUID.randomUUID());
        when(endpoints.findById(any())).thenReturn(Optional.of(endpoint));
        WebhookDelivery delivery = pending();
        when(deliveries.lockDueDeliveries(NOW, 20)).thenReturn(List.of(delivery));

        service.deliverDue();

        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        verify(transport, never()).post(any(), anyMap(), anyString());
    }

    @Test
    void theBackoffDoublesAndIsCappedAtAnHour() {
        Duration first = service.backoff(1);
        Duration second = service.backoff(2);
        Duration capped = service.backoff(12);

        assertThat(first).isBetween(Duration.ofSeconds(24), Duration.ofSeconds(36));
        assertThat(second).isBetween(Duration.ofSeconds(48), Duration.ofSeconds(72));
        assertThat(capped).isBetween(Duration.ofMinutes(48), Duration.ofMinutes(72));
    }

    private WebhookDelivery pending() {
        return new WebhookDelivery(endpoint.getId(), MERCHANT, UUID.randomUUID(), UUID.randomUUID(), BODY);
    }
}
