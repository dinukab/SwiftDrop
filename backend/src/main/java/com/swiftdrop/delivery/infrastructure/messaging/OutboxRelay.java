package com.swiftdrop.delivery.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** One database-locked poll; RabbitMQ acknowledgements never commit database state themselves. */
public final class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RabbitTemplate rabbit;
    private final MeterRegistry meters;
    private final Clock clock;
    private final int batch;
    private final int confirmSeconds;
    private volatile long backlog;
    private volatile long oldestAgeSeconds;
    private volatile long lastCompletedEpoch;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, RabbitTemplate rabbit,
            MeterRegistry meters, Clock clock, int batch, int confirmSeconds) {
        if (batch < 1 || batch > 500 || confirmSeconds < 1 || confirmSeconds > 30) {
            throw new IllegalArgumentException("Invalid outbox batch or confirm timeout");
        }
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.rabbit = rabbit;
        this.meters = meters;
        this.clock = clock;
        this.batch = batch;
        this.confirmSeconds = confirmSeconds;
        meters.gauge("swiftdrop.outbox.backlog", this, relay -> relay.backlog);
        meters.gauge("swiftdrop.outbox.oldest.age.seconds", this, relay -> relay.oldestAgeSeconds);
        meters.gauge("swiftdrop.outbox.last.completed.epoch.seconds", this, relay -> relay.lastCompletedEpoch);
    }

    @Scheduled(fixedDelayString = "${swiftdrop.messaging.relay-poll-ms:1000}")
    public void poll() {
        try {
            transactions.executeWithoutResult(tx -> {
                // Transaction-scoped: no stale lock after a crash. Shared by every relay instance.
                if (!Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(73420401)", Boolean.class))) {
                    return;
                }
                Long count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM parcel_outbox_events WHERE published_at IS NULL", Long.class);
                Timestamp oldest = jdbc.queryForObject(
                        "SELECT MIN(created_at) FROM parcel_outbox_events WHERE published_at IS NULL", Timestamp.class);
                backlog = count == null ? 0 : count;
                oldestAgeSeconds = oldest == null ? 0 : Math.max(0,
                        java.time.Duration.between(oldest.toInstant(), clock.instant()).getSeconds());
                if (oldestAgeSeconds > 120) {
                    log.error("Outbox backlog overdue: count={}, oldestAgeSeconds={}", backlog, oldestAgeSeconds);
                }
                var blocked = new HashSet<UUID>();
                long cursor = 0;
                int attempted = 0;
                // Keyset pages let other parcels progress even if an old parcel has many blocked rows.
                while (attempted < batch) {
                    var rows = jdbc.query("""
                            SELECT id, event_id, parcel_id, event_type, payload::text, created_at,
                                   attempts, next_attempt_at
                            FROM parcel_outbox_events
                            WHERE published_at IS NULL AND id > ? ORDER BY id LIMIT ?
                            """, (rs, n) -> new Row(rs.getLong("id"), rs.getObject("event_id", UUID.class),
                            rs.getObject("parcel_id", UUID.class), rs.getString("event_type"),
                            rs.getString("payload"), rs.getTimestamp("created_at").toInstant(),
                            rs.getInt("attempts"), rs.getTimestamp("next_attempt_at").toInstant()), cursor, batch);
                    if (rows.isEmpty()) {
                        break;
                    }
                    for (Row row : rows) {
                        cursor = row.id();
                        if (blocked.contains(row.parcelId())) {
                            continue;
                        }
                        if (row.nextAttempt().isAfter(clock.instant())) {
                            blocked.add(row.parcelId());
                            continue;
                        }
                        attempted++;
                        Outcome outcome = publish(row);
                        if (outcome == Outcome.SUCCESS) {
                            // Failure here rolls back the poll: republish the SAME event next time.
                            jdbc.update("""
                                    UPDATE parcel_outbox_events SET published_at = ?, attempts = attempts + 1,
                                    last_failure_category = NULL WHERE id = ?
                                    """, Timestamp.from(clock.instant()), row.id());
                        } else {
                            blocked.add(row.parcelId());
                            long delay = Math.min(60, 1L << Math.min(row.attempts(), 6));
                            jdbc.update("""
                                    UPDATE parcel_outbox_events SET attempts = attempts + 1,
                                    last_failure_category = ?, next_attempt_at = ? WHERE id = ?
                                    """, outcome.name(), Timestamp.from(clock.instant().plusSeconds(delay)), row.id());
                            if (row.attempts() + 1 >= 10 || row.createdAt().isBefore(clock.instant().minusSeconds(120))) {
                                log.error("Outbox publication delayed: eventId={}, parcelId={}, category={}",
                                        row.eventId(), row.parcelId(), outcome);
                            }
                        }
                        meters.counter("swiftdrop.outbox.publications", "outcome", outcome.name()).increment();
                        if (outcome == Outcome.BROKER_UNAVAILABLE || attempted >= batch) {
                            return;
                        }
                    }
                }
            });
            lastCompletedEpoch = clock.instant().getEpochSecond();
        } catch (RuntimeException ex) {
            // Do not log payloads, broker errors or database exception details.
            meters.counter("swiftdrop.outbox.poll.failures").increment();
            log.error("Outbox poll rolled back; unpublished events retained");
        }
    }

    Outcome publish(Row row) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(row.eventId().toString());
        properties.setType(row.type());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setTimestamp(Date.from(row.createdAt()));
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbit.send("swiftdrop.events", row.type(),
                    new Message(row.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
            var confirm = correlation.getFuture().get(confirmSeconds, TimeUnit.SECONDS);
            if (correlation.getReturned() != null) {
                return Outcome.UNROUTABLE;
            }
            return confirm.ack() ? Outcome.SUCCESS : Outcome.NACK;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Outcome.BROKER_UNAVAILABLE;
        } catch (TimeoutException | java.util.concurrent.ExecutionException | AmqpException ex) {
            return Outcome.BROKER_UNAVAILABLE;
        }
    }

    enum Outcome { SUCCESS, UNROUTABLE, NACK, BROKER_UNAVAILABLE }

    record Row(long id, UUID eventId, UUID parcelId, String type, String payload,
            Instant createdAt, int attempts, Instant nextAttempt) {
    }
}
