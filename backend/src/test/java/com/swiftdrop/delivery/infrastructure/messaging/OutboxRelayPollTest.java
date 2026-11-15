package com.swiftdrop.delivery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayPollTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    Instant now = Instant.parse("2026-11-14T00:00:00Z");
    OutboxRelay relay = new OutboxRelay(jdbc, new TransactionTemplate(manager), rabbit,
            new SimpleMeterRegistry(), Clock.fixed(now, ZoneOffset.UTC), 100, 1);

    @BeforeEach
    void transaction() {
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForObject(contains("pg_try_advisory"), eq(Boolean.class))).thenReturn(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void rows(OutboxRelay.Row... rows) {
        doReturn(List.of(rows), List.of()).when(jdbc).query(anyString(), any(RowMapper.class), anyLong(), anyInt());
    }

    OutboxRelay.Row row(long id, UUID parcel, Instant due) {
        return new OutboxRelay.Row(id, UUID.randomUUID(), parcel, "parcel.status-changed",
                "{}", now, 0, due);
    }

    void confirm(boolean ack) {
        doAnswer(call -> {
            CorrelationData data = call.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(ack, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void anotherRelayHoldingLockPreventsPublication() {
        when(jdbc.queryForObject(contains("pg_try_advisory"), eq(Boolean.class))).thenReturn(false);
        relay.poll();
        verifyNoInteractions(rabbit);
    }

    @Test
    void delayedParcelDoesNotBlockOtherParcels() {
        UUID parcel = UUID.randomUUID();
        rows(row(1, parcel, now.plusSeconds(30)), row(2, parcel, now), row(3, UUID.randomUUID(), now));
        confirm(true);
        relay.poll();
        verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        verify(jdbc).update(contains("published_at ="), any(java.sql.Timestamp.class), eq(3L));
    }

    @Test
    void nackBlocksOnlyItsParcelAndRecordsBackoff() {
        UUID parcel = UUID.randomUUID();
        rows(row(1, parcel, now), row(2, parcel, now), row(3, UUID.randomUUID(), now));
        confirm(false);
        relay.poll();
        verify(rabbit, times(2)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        verify(jdbc).update(contains("next_attempt_at ="), eq("NACK"),
                eq(java.sql.Timestamp.from(now.plusSeconds(1))), eq(1L));
        verify(jdbc, never()).update(contains("published_at ="), any(), any());
    }

    @Test
    void connectionFailureStopsOtherParcelsInThisPoll() {
        rows(row(1, UUID.randomUUID(), now), row(2, UUID.randomUUID(), now));
        doThrow(new AmqpConnectException(new java.net.ConnectException())).when(rabbit)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        relay.poll();
        verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void failedDatabaseMarkRollsBackAndNextPollRepublishesSameEvent() {
        var row = row(1, UUID.randomUUID(), now);
        rows(row);
        confirm(true);
        when(jdbc.update(contains("published_at ="), any(java.sql.Timestamp.class), eq(1L)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("simulated"));
        relay.poll();
        verify(manager).rollback(any());
        rows(row);
        relay.poll();
        var messages = ArgumentCaptor.forClass(Message.class);
        verify(rabbit, times(2)).send(anyString(), anyString(), messages.capture(), any(CorrelationData.class));
        assertThat(messages.getAllValues()).allSatisfy(message ->
                assertThat(message.getMessageProperties().getMessageId()).isEqualTo(row.eventId().toString()));
    }
}
