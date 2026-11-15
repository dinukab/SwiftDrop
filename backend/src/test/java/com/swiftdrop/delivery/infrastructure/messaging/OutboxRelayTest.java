package com.swiftdrop.delivery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void publishesEachEventOnceInOrderAndMarksItPublishedAfterConfirmation() {
        OutboxEvent first = event(1);
        OutboxEvent second = event(2);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(outbox.lockUnpublished(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
        confirmEverything(rabbit);

        int published = new OutboxRelay(outbox, rabbit, Clock.fixed(NOW, ZoneOffset.UTC)).relayBatch();

        assertThat(published).isEqualTo(2);
        assertThat(first.isPublished()).isTrue();
        assertThat(second.isPublished()).isTrue();
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void theBodyIsSentUnchangedWithItsIdentityAndRoutingKey() {
        OutboxEvent event = event(1);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(outbox.lockUnpublished(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(event));
        confirmEverything(rabbit);

        new OutboxRelay(outbox, rabbit, Clock.fixed(NOW, ZoneOffset.UTC)).relayBatch();

        verify(rabbit).send(eq("swiftdrop.events"), eq("parcel.status-changed"), any(Message.class),
                any(CorrelationData.class));
        org.mockito.ArgumentCaptor<Message> sent = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(anyString(), anyString(), sent.capture(), any(CorrelationData.class));
        assertThat(new String(sent.getValue().getBody())).isEqualTo(event.getPayload());
        assertThat(sent.getValue().getMessageProperties().getMessageId()).isEqualTo(event.getEventId().toString());
        assertThat(sent.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
    }

    @Test
    void anUnroutableEventStopsTheRunAndIsRetriedLater() {
        OutboxEvent first = event(1);
        OutboxEvent second = event(2);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(outbox.lockUnpublished(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
        returnEverything(rabbit);

        int published = new OutboxRelay(outbox, rabbit, Clock.fixed(NOW, ZoneOffset.UTC)).relayBatch();

        assertThat(published).isZero();
        assertThat(first.isPublished()).isFalse();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastFailureCategory()).isEqualTo("NOT_ROUTED");
        assertThat(first.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        // The later event must not overtake the failed one
        assertThat(second.isPublished()).isFalse();
        verify(rabbit, org.mockito.Mockito.times(1)).send(anyString(), anyString(), any(Message.class),
                any(CorrelationData.class));
    }

    @Test
    void anEventNotYetDueWaitsAndNothingAfterIsSent() {
        OutboxEvent waiting = event(1);
        waiting.markFailed("NOT_CONFIRMED", NOW.plusSeconds(600));
        OutboxEvent later = event(2);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(outbox.lockUnpublished(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(waiting, later));

        int published = new OutboxRelay(outbox, rabbit, Clock.fixed(NOW, ZoneOffset.UTC)).relayBatch();

        assertThat(published).isZero();
        verify(rabbit, never()).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void backoffGrowsFromOneMinuteAndStopsAtAnHour() {
        assertThat(OutboxRelay.backoff(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(OutboxRelay.backoff(2)).isEqualTo(Duration.ofMinutes(4));
        assertThat(OutboxRelay.backoff(20)).isEqualTo(Duration.ofMinutes(60));
    }

    private static OutboxEvent event(int sequence) {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), sequence, "parcel.status-changed",
                "{\"sequence\":" + sequence + "}", NOW.minusSeconds(60));
    }

    /** The broker confirms every publish and returns none. */
    private static void confirmEverything(RabbitTemplate rabbit) {
        org.mockito.Mockito.doAnswer((InvocationOnMock call) -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    /** The broker confirms each publish but returns it: no queue is bound to the key. */
    private static void returnEverything(RabbitTemplate rabbit) {
        org.mockito.Mockito.doAnswer((InvocationOnMock call) -> {
            Message message = call.getArgument(2);
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new org.springframework.amqp.core.ReturnedMessage(
                    message, 312, "NO_ROUTE", "swiftdrop.events", "parcel.status-changed"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
