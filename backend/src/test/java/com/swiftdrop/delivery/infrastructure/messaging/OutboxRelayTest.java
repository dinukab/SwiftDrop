package com.swiftdrop.delivery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Instant now = Instant.parse("2026-11-14T00:00:00Z");
    private final OutboxRelay relay = new OutboxRelay(mock(JdbcTemplate.class),
            mock(TransactionTemplate.class), rabbit, new SimpleMeterRegistry(),
            Clock.fixed(now, ZoneOffset.UTC), 100, 1);

    @Test
    void confirmedPersistentEventUsesOriginalIdentityAndBytesOnEveryAttempt() {
        var row = row();
        doAnswer(call -> {
            Message message = call.getArgument(2);
            assertThat(message.getBody()).isEqualTo(row.payload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(row.eventId().toString());
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(message.getMessageProperties().getTimestamp().toInstant()).isEqualTo(now);
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(relay.publish(row)).isEqualTo(OutboxRelay.Outcome.SUCCESS);
        assertThat(relay.publish(row)).isEqualTo(OutboxRelay.Outcome.SUCCESS);
        verify(rabbit, times(2)).send(eq("swiftdrop.events"), eq("parcel.status-changed"),
                any(Message.class), any(CorrelationData.class));
    }

    @Test
    void positiveConfirmWithReturnIsNotSuccess() {
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE",
                    "swiftdrop.events", "parcel.status-changed"));
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(relay.publish(row())).isEqualTo(OutboxRelay.Outcome.UNROUTABLE);
    }

    @Test
    void nackIsRetryable() {
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(relay.publish(row())).isEqualTo(OutboxRelay.Outcome.NACK);
    }

    @Test
    void confirmTimeoutStopsPoll() {
        assertThat(relay.publish(row())).isEqualTo(OutboxRelay.Outcome.BROKER_UNAVAILABLE);
    }

    @Test
    void connectionFailureStopsPoll() {
        doThrow(new AmqpConnectException(new java.net.ConnectException())).when(rabbit)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(relay.publish(row())).isEqualTo(OutboxRelay.Outcome.BROKER_UNAVAILABLE);
    }

    private OutboxRelay.Row row() {
        return new OutboxRelay.Row(1, UUID.randomUUID(), UUID.randomUUID(), "parcel.status-changed",
                "{\"schemaVersion\":1}", now, 0, now);
    }
}
