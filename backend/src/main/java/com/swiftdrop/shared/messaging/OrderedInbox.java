package com.swiftdrop.shared.messaging;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Technical ordered inbox. The owner supplies its table prefix, history and transactional effect.
 * Returning false from an effect preserves the pending event without claiming processing succeeded.
 */
public final class OrderedInbox {
    private static final Logger log = LoggerFactory.getLogger(OrderedInbox.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String prefix;
    private final History history;
    private final Predicate<Event> effect;
    private final Clock clock;
    private final MeterRegistry meters;

    public OrderedInbox(JdbcTemplate jdbc, TransactionTemplate transactions, String prefix,
            History history, Predicate<Event> effect, Clock clock, MeterRegistry meters) {
        if (!prefix.matches("[a-z]+_event")) {
            throw new IllegalArgumentException("Invalid inbox table prefix");
        }
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.prefix = prefix;
        this.history = history;
        this.effect = effect;
        this.clock = clock;
        this.meters = meters;
    }

    public void accept(Event event) {
        transactions.executeWithoutResult(tx -> {
            int last = lock(event.aggregateId());
            if (duplicate(event, last)) {
                return;
            }
            store(event);
            if (event.sequence() > last + 1) {
                meters.counter("swiftdrop.inbox.gaps", "consumer", prefix).increment();
            }
            drain(event.aggregateId(), last);
        });
    }

    private int lock(UUID id) {
        jdbc.update("INSERT INTO " + prefix + "_state (aggregate_id, next_attempt_at)"
                + " VALUES (?, ?) ON CONFLICT DO NOTHING", id, Timestamp.from(clock.instant()));
        return jdbc.queryForObject("SELECT last_sequence FROM " + prefix
                + "_state WHERE aggregate_id = ? FOR UPDATE", Integer.class, id);
    }

    private boolean duplicate(Event event, int last) {
        boolean known = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM "
                + prefix + "_processed WHERE event_id = ?)", Boolean.class, event.id()));
        if (known || event.sequence() <= last) {
            meters.counter("swiftdrop.inbox.duplicates", "consumer", prefix).increment();
            if (!known) {
                meters.counter("swiftdrop.inbox.anomalies", "consumer", prefix).increment();
            }
            return true;
        }
        return false;
    }

    private void store(Event event) {
        jdbc.update("INSERT INTO " + prefix + "_pending"
                + " (event_id, aggregate_id, sequence, payload, received_at) VALUES (?, ?, ?, ?, ?)"
                + " ON CONFLICT (event_id) DO NOTHING", event.id(), event.aggregateId(), event.sequence(),
                event.payload(), Timestamp.from(clock.instant()));
    }

    private void drain(UUID id, int last) {
        while (true) {
            List<Event> next = jdbc.query("SELECT event_id, aggregate_id, sequence, payload FROM "
                    + prefix + "_pending WHERE aggregate_id = ? AND sequence = ?",
                    (rs, n) -> new Event(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getInt(3), rs.getString(4)), id, last + 1);
            if (next.isEmpty()) {
                return;
            }
            Event event = next.getFirst();
            if (!effect.test(event)) {
                meters.counter("swiftdrop.inbox.effect.deferred", "consumer", prefix).increment();
                return;
            }
            // These writes and the business effect are in the same transaction.
            jdbc.update("INSERT INTO " + prefix + "_processed (event_id, processed_at) VALUES (?, ?)",
                    event.id(), Timestamp.from(clock.instant()));
            last = event.sequence();
            jdbc.update("UPDATE " + prefix + "_state SET last_sequence = ?, attempts = 0, stuck = FALSE"
                    + " WHERE aggregate_id = ?", last, id);
            jdbc.update("DELETE FROM " + prefix + "_pending WHERE event_id = ?", event.id());
            meters.counter("swiftdrop.inbox.applied", "consumer", prefix).increment();
        }
    }

    public void reconcile() {
        var ids = jdbc.queryForList("SELECT s.aggregate_id FROM " + prefix + "_state s WHERE NOT s.stuck"
                + " AND s.next_attempt_at <= ? AND EXISTS (SELECT 1 FROM " + prefix + "_pending p"
                + " WHERE p.aggregate_id = s.aggregate_id AND p.received_at <= ?) ORDER BY s.next_attempt_at LIMIT 100",
                UUID.class, Timestamp.from(clock.instant()), Timestamp.from(clock.instant().minusSeconds(60)));
        for (UUID id : ids) {
            try {
                transactions.executeWithoutResult(tx -> {
                    int last = lock(id);
                    if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT stuck OR next_attempt_at > ? FROM "
                            + prefix + "_state WHERE aggregate_id = ?", Boolean.class,
                            Timestamp.from(clock.instant()), id))) {
                        return;
                    }
                    Integer end = jdbc.queryForObject("SELECT MAX(sequence) FROM " + prefix
                            + "_pending WHERE aggregate_id = ?", Integer.class, id);
                    if (end == null) {
                        return;
                    }
                    List<Event> recovered = history.between(id, last + 1, end);
                    int expected = last + 1;
                    for (Event event : recovered) {
                        if (!event.aggregateId().equals(id) || event.sequence() != expected++) {
                            throw new IllegalStateException("Noncontiguous event history");
                        }
                        if (!duplicate(event, last)) {
                            store(event);
                        }
                    }
                    if (expected != end + 1) {
                        throw new IllegalStateException("Incomplete event history");
                    }
                    drain(id, last);
                    jdbc.update("UPDATE " + prefix + "_state SET next_attempt_at = ? WHERE aggregate_id = ?",
                            Timestamp.from(clock.instant().plusSeconds(60)), id);
                });
            } catch (RuntimeException ex) {
                transactions.executeWithoutResult(tx -> {
                    lock(id);
                    int attempts = jdbc.queryForObject("SELECT attempts FROM " + prefix
                            + "_state WHERE aggregate_id = ?", Integer.class, id) + 1;
                    jdbc.update("UPDATE " + prefix
                            + "_state SET attempts = ?, stuck = ?, next_attempt_at = ? WHERE aggregate_id = ?",
                            attempts, attempts >= 5,
                            Timestamp.from(clock.instant().plusSeconds(60L << Math.min(attempts - 1, 4))), id);
                    meters.counter("swiftdrop.inbox.reconciliation.failures", "consumer", prefix).increment();
                    if (attempts >= 5) {
                        meters.counter("swiftdrop.inbox.stuck", "consumer", prefix).increment();
                        log.error("Inbox reconciliation stuck: consumer={}, aggregateId={}", prefix, id);
                    }
                });
            }
        }
    }

    public record Event(UUID id, UUID aggregateId, int sequence, String payload) {
        public Event {
            java.util.Objects.requireNonNull(id);
            java.util.Objects.requireNonNull(aggregateId);
            java.util.Objects.requireNonNull(payload);
            if (sequence < 1) {
                throw new IllegalArgumentException("Invalid sequence");
            }
        }
    }

    @FunctionalInterface
    public interface History {
        List<Event> between(UUID id, int from, int to);
    }
}
