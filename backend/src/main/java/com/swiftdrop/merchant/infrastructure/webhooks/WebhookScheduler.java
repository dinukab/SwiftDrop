package com.swiftdrop.merchant.infrastructure.webhooks;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.swiftdrop.merchant.application.WebhookDeliveryService;

/** Runs the webhook sender on a fixed delay. Off unless {@code swiftdrop.webhooks.enabled=true}. */
@Component
@ConditionalOnProperty(name = "swiftdrop.webhooks.enabled", havingValue = "true")
public class WebhookScheduler {

    private final WebhookDeliveryService deliveries;

    public WebhookScheduler(WebhookDeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.webhooks.interval-ms:5000}")
    public void run() {
        deliveries.deliverDue();
    }
}
