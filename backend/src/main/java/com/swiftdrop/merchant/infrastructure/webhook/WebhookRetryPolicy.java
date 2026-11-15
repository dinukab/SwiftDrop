package com.swiftdrop.merchant.infrastructure.webhook;

import java.util.concurrent.ThreadLocalRandom;

/** ADR 0004 bounds apply equally to automatic retries and manual attempt series. */
public record WebhookRetryPolicy(int maxAttempts, int baseSeconds, int capSeconds, int maxRedeliveries) {
    public static WebhookRetryPolicy defaults() { return new WebhookRetryPolicy(8, 30, 3600, 3); }

    public WebhookRetryPolicy {
        if (maxAttempts < 3 || maxAttempts > 15 || baseSeconds < 1 || baseSeconds > 300
                || capSeconds < baseSeconds || capSeconds > 3600 || maxRedeliveries < 1 || maxRedeliveries > 10) {
            throw new IllegalArgumentException("Invalid webhook retry policy");
        }
    }

    public long delay(int attempt, long retryAfter) {
        return delay(attempt, retryAfter, ThreadLocalRandom.current().nextDouble(.8, 1.2));
    }

    long delay(int attempt, long retryAfter, double jitter) {
        long exponential = (long) baseSeconds * (1L << Math.clamp(attempt - 1, 0, 14));
        return Math.min(capSeconds, Math.max(Math.round(exponential * jitter), Math.max(0, retryAfter)));
    }
}
