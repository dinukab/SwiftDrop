package com.swiftdrop.merchant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WebhookDeliveryRedeliveryTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void aFailedDeliveryIsSentAgainWithAFreshSeriesOfAttempts() {
        WebhookDelivery delivery = failed();

        delivery.redeliver(NOW);

        assertThat(delivery.getStatus()).isEqualTo("PENDING");
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getManualRedeliveries()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void onlyAFailedDeliveryCanBeRedelivered() {
        WebhookDelivery pending = new WebhookDelivery(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "{}");
        WebhookDelivery done = new WebhookDelivery(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "{}");
        done.recordSuccess(200);

        assertThat(pending.canRedeliver()).isFalse();
        assertThat(done.canRedeliver()).isFalse();
        assertThatThrownBy(() -> pending.redeliver(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> done.redeliver(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theManualLimitIsThreeRedeliveries() {
        WebhookDelivery delivery = failed();
        for (int i = 0; i < WebhookDelivery.MAX_MANUAL_REDELIVERIES; i++) {
            delivery.redeliver(NOW);
            delivery.recordFailure(500);
        }

        assertThat(delivery.getManualRedeliveries()).isEqualTo(3);
        assertThat(delivery.canRedeliver()).isFalse();
        assertThatThrownBy(() -> delivery.redeliver(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theBodyAndEventAreUnchangedByARedelivery() {
        WebhookDelivery delivery = failed();
        UUID event = delivery.getEventId();

        delivery.redeliver(NOW);

        assertThat(delivery.getPayload()).isEqualTo("{\"eventId\":\"x\"}");
        assertThat(delivery.getEventId()).isEqualTo(event);
    }

    private static WebhookDelivery failed() {
        WebhookDelivery delivery = new WebhookDelivery(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "{\"eventId\":\"x\"}");
        delivery.recordFailure(404);
        return delivery;
    }
}
