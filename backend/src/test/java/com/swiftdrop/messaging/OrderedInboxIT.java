package com.swiftdrop.messaging;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.swiftdrop.shared.messaging.OrderedInbox;

@Testcontainers
class OrderedInboxIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate transactions;
    UUID parcel;
    final Instant now = Instant.parse("2026-11-14T00:00:00Z");
    final List<Integer> applied = new ArrayList<>();

    @BeforeAll
    static void database() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @BeforeEach
    void setUp() {
        parcel = UUID.randomUUID();
        jdbc.update("DELETE FROM merchant_event_pending");
        jdbc.update("DELETE FROM merchant_event_processed");
        jdbc.update("DELETE FROM merchant_event_state");
    }

    OrderedInbox inbox(OrderedInbox.History history, java.util.function.Predicate<OrderedInbox.Event> effect,
            Instant time) {
        return new OrderedInbox(jdbc, transactions, "merchant_event", history, effect,
                Clock.fixed(time, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    OrderedInbox.Event event(int sequence) {
        return new OrderedInbox.Event(UUID.randomUUID(), parcel, sequence, "{}");
    }

    @Test
    void gapWaitsAndMissingEventDrainsInOrderOnce() {
        var inbox = inbox((id, from, to) -> List.of(), e -> { applied.add(e.sequence()); return true; }, now);
        var first = event(1);
        var second = event(2);
        inbox.accept(second);
        assertThat(applied).isEmpty();
        inbox.accept(first);
        inbox.accept(first);
        inbox.accept(second);
        assertThat(applied).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_event_pending", Integer.class)).isZero();
    }

    @Test
    void missingEffectKeepsEventPendingAndUnprocessed() {
        var inbox = inbox((id, from, to) -> List.of(), e -> false, now);
        inbox.accept(event(1));
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM merchant_event_state", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_event_pending", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_event_processed", Integer.class)).isZero();
    }

    @Test
    void reconciliationRestoresMissingEventsAndLateOriginalIsDuplicate() {
        var first = event(1);
        var second = event(2);
        var history = (OrderedInbox.History) (id, from, to) -> List.of(first, second);
        inbox(history, e -> true, now).accept(second);
        var recovered = inbox(history, e -> { applied.add(e.sequence()); return true; }, now.plusSeconds(61));
        recovered.reconcile();
        recovered.accept(first);
        assertThat(applied).containsExactly(1, 2);
    }

    @Test
    void failedEffectRollsBackEffectAndDeduplicationTogether() {
        var retry = new AtomicBoolean(false);
        var inbox = inbox((id, from, to) -> List.of(), e -> {
            jdbc.update("INSERT INTO notification_messages VALUES (?, ?, ?, ?, ?, ?)",
                    e.id(), UUID.randomUUID(), parcel, "DEMO", "PICKED_UP", java.sql.Timestamp.from(now));
            if (!retry.get()) {
                throw new IllegalStateException("simulate failure after business write");
            }
            return true;
        }, now);
        var event = event(1);
        assertThatThrownBy(() -> inbox.accept(event)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_messages WHERE event_id = ?",
                Integer.class, event.id())).isZero();
        retry.set(true);
        inbox.accept(event);
        inbox.accept(event);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_messages WHERE event_id = ?",
                Integer.class, event.id())).isOne();
    }

    @Test
    void repeatedMissingHistoryMarksStuckWithoutDroppingPendingEvent() {
        var missing = (OrderedInbox.History) (id, from, to) -> List.of();
        inbox(missing, e -> true, now).accept(event(2));
        for (int attempt = 1; attempt <= 5; attempt++) {
            inbox(missing, e -> true, now.plusSeconds(attempt * 2000L)).reconcile();
        }
        assertThat(jdbc.queryForObject("SELECT stuck FROM merchant_event_state", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_event_pending", Integer.class)).isOne();
    }
}
