package com.swiftdrop.merchant.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.merchant.domain.WebhookDelivery;
import com.swiftdrop.merchant.domain.WebhookDeliveryRepository;
import com.swiftdrop.merchant.domain.WebhookEndpoint;
import com.swiftdrop.merchant.domain.WebhookEndpointRepository;

/**
 * A merchant's view of its webhook deliveries, and manual redelivery of failed ones (Phase 4 item 6).
 * Works whether or not the sender is running: the history is stored data.
 */
@Service
@Lazy
public class WebhookDeliveryHistoryService {

    static final int MAX_PAGE_SIZE = 100;

    private final WebhookDeliveryRepository deliveries;
    private final WebhookEndpointRepository endpoints;
    private final Clock clock;

    @Autowired
    public WebhookDeliveryHistoryService(WebhookDeliveryRepository deliveries, WebhookEndpointRepository endpoints) {
        this(deliveries, endpoints, Clock.systemUTC());
    }

    WebhookDeliveryHistoryService(WebhookDeliveryRepository deliveries, WebhookEndpointRepository endpoints,
            Clock clock) {
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The merchant's deliveries, newest first. */
    @Transactional(readOnly = true)
    public Page<WebhookDelivery> history(UUID merchantId, int page, int size) {
        Objects.requireNonNull(merchantId, "merchantId");
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return deliveries.findByMerchantIdOrderByCreatedAtDesc(merchantId,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * Sends a failed delivery again.
     *
     * @throws MerchantNotFoundException if the delivery is not this merchant's
     * @throws WebhookDeliveryNotRedeliverableException if it has not failed, its endpoint was removed,
     *         or it has used its manual redeliveries
     */
    @Transactional
    public WebhookDelivery redeliver(UUID deliveryId, UUID merchantId) {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(merchantId, "merchantId");

        WebhookDelivery delivery = deliveries.findByIdAndMerchantId(deliveryId, merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Webhook delivery not found: " + deliveryId));

        if (!"FAILED".equals(delivery.getStatus())) {
            throw new WebhookDeliveryNotRedeliverableException("Only a failed delivery can be sent again");
        }
        WebhookEndpoint endpoint = endpoints.findById(delivery.getEndpointId()).orElse(null);
        if (endpoint == null || !endpoint.isActive()) {
            throw new WebhookDeliveryNotRedeliverableException("The webhook endpoint is no longer active");
        }
        if (delivery.getManualRedeliveries() >= WebhookDelivery.MAX_MANUAL_REDELIVERIES) {
            throw new WebhookDeliveryNotRedeliverableException("This delivery has been sent again the maximum number of times");
        }

        Instant now = clock.instant();
        delivery.redeliver(now);
        return deliveries.save(delivery);
    }
}
