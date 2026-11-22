package com.swiftdrop.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.impl.LongStringHelper;

import com.swiftdrop.notification.application.DeadLetter;

class RabbitDeadLetterQueueTest {

    private static final UUID EVENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PARCEL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant DIED = Instant.parse("2026-11-21T09:00:00Z");

    @Test
    void theEventIdAndStatusComeFromTheBodyAndTheReasonFromTheBrokerHeaders() {
        AMQP.BasicProperties props = props(Map.of(
                "x-first-death-reason", LongStringHelper.asLongString("rejected"),
                "x-death", List.of(Map.of(
                        "reason", LongStringHelper.asLongString("rejected"),
                        "count", 2L,
                        "time", Date.from(DIED)))));

        DeadLetter letter = RabbitDeadLetterQueue.summarize(
                bytes("{\"eventId\":\"" + EVENT + "\",\"data\":{\"parcelId\":\"" + PARCEL
                        + "\",\"newStatus\":\"DELIVERED\"}}"),
                props);

        assertThat(letter.eventId()).isEqualTo(EVENT);
        assertThat(letter.status()).isEqualTo("DELIVERED");
        assertThat(letter.reason()).isEqualTo("rejected");
        assertThat(letter.deathCount()).isEqualTo(2);
        assertThat(letter.deadLetteredAt()).isEqualTo(DIED);
    }

    @Test
    void aBodyThatCannotBeParsedStillGivesASummaryWithoutAnEventId() {
        DeadLetter letter = RabbitDeadLetterQueue.summarize(bytes("not json at all"), props(Map.of()));

        assertThat(letter.eventId()).isNull();
        assertThat(letter.status()).isNull();
        assertThat(letter.reason()).isNull();
        assertThat(letter.deathCount()).isZero();
    }

    @Test
    void theReasonFallsBackToTheFirstDeathEntry() {
        AMQP.BasicProperties props = props(Map.of("x-death", List.of(Map.of(
                "reason", LongStringHelper.asLongString("delivery_limit"), "count", 1L))));

        DeadLetter letter = RabbitDeadLetterQueue.summarize(bytes("{}"), props);

        assertThat(letter.reason()).isEqualTo("delivery_limit");
        assertThat(letter.deathCount()).isEqualTo(1);
        assertThat(letter.deadLetteredAt()).isNull();
    }

    @Test
    void missingPropertiesDoNotFail() {
        DeadLetter letter = RabbitDeadLetterQueue.summarize(bytes("{}"), null);

        assertThat(letter.reason()).isNull();
        assertThat(letter.deathCount()).isZero();
    }

    private static AMQP.BasicProperties props(Map<String, Object> headers) {
        return new AMQP.BasicProperties.Builder().headers(headers).build();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
