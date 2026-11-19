package com.swiftdrop.merchant.application;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;

/**
 * Sends the pending webhook deliveries that are due (ADR 0004 section 7.1 and 9): signs each body,
 * posts it, and records the outcome. Success is any 2xx. Connection failures, timeouts, 408, 429 and
 * 5xx are retried with exponential backoff (30 s, doubling, plus or minus 20 % jitter, capped at one
 * hour), up to {@link #MAX_ATTEMPTS} attempts. Other responses fail the delivery at once.
 *
 * <p>Each batch runs in one transaction with its rows locked, so two senders never send the same
 * delivery. No secret, signature or body is ever logged.
 */
@Service
@ConditionalOnProperty(name = "swiftdrop.webhooks.enabled", havingValue = "true")
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    static final int MAX_ATTEMPTS = 8;
    static final int BATCH_SIZE = 20;
    static final String EVENT_TYPE = "parcel.status-changed";
    static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final WebhookSecretCipher cipher;
    private final WebhookUrlPolicy urlPolicy;
    private final WebhookTransport transport;
    private final Clock clock;
    private final Random jitter;

    @Autowired
    public WebhookDeliveryService(WebhookDeliveryRepository deliveries, WebhookEndpointRepository endpoints,
            WebhookSecretCipher cipher, WebhookUrlPolicy urlPolicy, WebhookTransport transport) {
        this(deliveries, endpoints, cipher, urlPolicy, transport, Clock.systemUTC(), new Random());
    }

    WebhookDeliveryService(WebhookDeliveryRepository deliveries, WebhookEndpointRepository endpoints,
            WebhookSecretCipher cipher, WebhookUrlPolicy urlPolicy, WebhookTransport transport, Clock clock,
            Random jitter) {
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
        this.urlPolicy = Objects.requireNonNull(urlPolicy, "urlPolicy");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.jitter = Objects.requireNonNull(jitter, "jitter");
    }

    /**
     * Sends one batch of due deliveries. Returns how many were attempted.
     */
    @Transactional
    public int deliverDue() {
        Instant now = clock.instant();
        List<WebhookDelivery> due = deliveries.lockDueDeliveries(now, BATCH_SIZE);
        for (WebhookDelivery delivery : due) {
            attempt(delivery, now);
        }
        return due.size();
    }

    private void attempt(WebhookDelivery delivery, Instant now) {
        Optional<WebhookEndpoint> found = endpoints.findById(delivery.getEndpointId());
        if (found.isEmpty() || !found.get().isActive() || delivery.getPayload() == null) {
            // Nothing to send to: the endpoint was removed, or the row predates stored bodies
            delivery.recordFailure(0);
            return;
        }
        WebhookEndpoint endpoint = found.get();

        String secret;
        URI uri;
        try {
            secret = cipher.decrypt(endpoint.getSecretEncrypted(), endpoint.getMerchantId());
            uri = urlPolicy.requireAllowed(endpoint.getUrl());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // The message is safe by construction (it names a rule, never a secret or URL)
            log.warn("Webhook delivery {} cannot be sent: {}", delivery.getId(), e.getMessage());
            delivery.recordFailure(0);
            return;
        }

        String timestamp = String.valueOf(now.getEpochSecond());
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "X-SwiftDrop-Event-Id", delivery.getEventId().toString(),
                "X-SwiftDrop-Event-Type", EVENT_TYPE,
                "X-SwiftDrop-Timestamp", timestamp,
                "X-SwiftDrop-Signature", WebhookSigner.sign(secret, timestamp, delivery.getPayload()));

        Integer status = transport.post(uri, headers, delivery.getPayload()).httpStatus();
        if (status != null && status >= 200 && status < 300) {
            delivery.recordSuccess(status);
            return;
        }
        boolean retryable = status == null || status == 408 || status == 429 || status >= 500;
        int attemptNumber = delivery.getAttemptCount() + 1;
        int code = status == null ? 0 : status;
        if (retryable && attemptNumber < MAX_ATTEMPTS) {
            delivery.recordRetry(code, now.plus(backoff(attemptNumber)));
        } else {
            delivery.recordFailure(code);
        }
    }

    /**
     * The wait before attempt {@code attemptNumber + 1}: 30 s after the first failure, doubling, capped
     * at one hour, with +-20 % jitter so many retries do not arrive together.
     */
    Duration backoff(int attemptNumber) {
        long seconds = BASE_BACKOFF.getSeconds() << Math.min(attemptNumber - 1, 10);
        long capped = Math.min(seconds, MAX_BACKOFF.getSeconds());
        double factor = 0.8 + 0.4 * jitter.nextDouble();
        return Duration.ofSeconds(Math.max(1L, Math.round(capped * factor)));
    }
}
