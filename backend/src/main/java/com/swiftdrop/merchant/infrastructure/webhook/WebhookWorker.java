package com.swiftdrop.merchant.infrastructure.webhook;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** Claim/commit, send without a transaction, then persist with a fencing token. */
public final class WebhookWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final WebhookSecrets secrets;
    private final WebhookHttpSender sender;
    private final Clock clock;
    private final MeterRegistry meters;

    public WebhookWorker(JdbcTemplate jdbc, TransactionTemplate transactions, WebhookSecrets secrets,
            WebhookHttpSender sender, Clock clock, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.secrets = secrets;
        this.sender = sender;
        this.clock = clock;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${swiftdrop.webhooks.poll-ms:5000}")
    public void poll() {
        try {
            for (int i = 0; i < 20; i++) {
                Job job = claim();
                if (job == null) break;
                send(job);
            }
        } catch (RuntimeException ex) {
            meters.counter("swiftdrop.webhooks.poll.failures").increment();
            org.slf4j.LoggerFactory.getLogger(WebhookWorker.class)
                    .error("Webhook sender unavailable; leased jobs will be recovered");
        }
    }

    Job claim() {
        return transactions.execute(tx -> {
            var rows = jdbc.query("""
                    SELECT d.id,d.event_id,d.payload,d.attempts,e.id AS endpoint_id,e.url,
                           e.key_id,e.nonce,e.ciphertext,e.active,m.status AS merchant_status
                    FROM merchant_webhook_deliveries d
                    JOIN merchant_webhook_endpoints e ON e.id=d.endpoint_id
                    JOIN merchants m ON m.id=e.merchant_id
                    WHERE d.state IN ('PENDING','SENDING','SECRET_UNAVAILABLE') AND d.next_attempt_at<=?
                    ORDER BY d.next_attempt_at,d.id LIMIT 1 FOR UPDATE OF d SKIP LOCKED
                    """, (rs, n) -> new Job(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                            rs.getString("payload"), rs.getInt("attempts"), rs.getObject("endpoint_id", UUID.class),
                            rs.getString("url"), new WebhookSecrets.Encrypted(rs.getString("key_id"),
                                    rs.getBytes("nonce"), rs.getBytes("ciphertext")),
                            rs.getBoolean("active") && "APPROVED".equals(rs.getString("merchant_status")), UUID.randomUUID()),
                    Timestamp.from(clock.instant()));
            if (rows.isEmpty()) return null;
            Job job = rows.getFirst();
            jdbc.update("""
                    UPDATE merchant_webhook_deliveries SET state='SENDING',lease_token=?,next_attempt_at=?
                    WHERE id=?
                    """, job.token(), Timestamp.from(clock.instant().plusSeconds(120)), job.id());
            return job;
        });
    }

    void send(Job job) {
        if (!job.active() || job.attempts() >= 8) {
            jdbc.update("UPDATE merchant_webhook_deliveries SET state='FAILED',lease_token=NULL WHERE id=? AND lease_token=?",
                    job.id(), job.token());
            return;
        }
        byte[] secret;
        try { secret = secrets.decrypt(job.endpointId(), job.encrypted()); }
        catch (WebhookSecrets.SecretUnavailable ex) {
            jdbc.update("""
                    UPDATE merchant_webhook_deliveries SET state='SECRET_UNAVAILABLE',lease_token=NULL,next_attempt_at=?
                    WHERE id=? AND lease_token=?
                    """, Timestamp.from(clock.instant().plusSeconds(60)), job.id(), job.token());
            meters.counter("swiftdrop.webhooks.secret.unavailable").increment();
            org.slf4j.LoggerFactory.getLogger(WebhookWorker.class)
                    .error("Webhook secret unavailable endpoint={} keyId={}", job.endpointId(), job.encrypted().keyId());
            return;
        }
        try {
            int attempt = job.attempts() + 1;
            Instant started = clock.instant();
            Boolean reserved = transactions.execute(tx -> {
                int updated = jdbc.update("""
                        UPDATE merchant_webhook_deliveries SET attempts=? WHERE id=? AND lease_token=?
                        """, attempt, job.id(), job.token());
                if (updated == 0) return false;
                // A crash leaves an explicit uncertain attempt; the lease permits a bounded retry.
                jdbc.update("""
                        INSERT INTO merchant_webhook_attempts(delivery_id,attempt,attempted_at,outcome,duration_ms,next_attempt_at)
                        VALUES (?,?,?,'IN_FLIGHT',0,?)
                        """, job.id(), attempt, Timestamp.from(started), Timestamp.from(started.plusSeconds(120)));
                return true;
            });
            if (!Boolean.TRUE.equals(reserved)) return;
            long nano = System.nanoTime();
            var result = sender.send(job.url(), job.eventId(), job.payload(), secret);
            long duration = (System.nanoTime() - nano) / 1_000_000;
            boolean retry = result.retryable() && attempt < 8;
            Instant next = retry ? clock.instant().plusSeconds(delay(attempt, result.retryAfterSeconds())) : null;
            transactions.executeWithoutResult(tx -> {
                int updated = jdbc.update("""
                        UPDATE merchant_webhook_deliveries SET state=?,lease_token=NULL,next_attempt_at=?
                        WHERE id=? AND lease_token=?
                        """, result.outcome().equals("SUCCESS") ? "SUCCEEDED" : retry ? "PENDING" : "FAILED",
                        Timestamp.from(next == null ? clock.instant() : next), job.id(), job.token());
                if (updated == 0) return; // A newer lease owns the record.
                jdbc.update("""
                        UPDATE merchant_webhook_attempts SET outcome=?,http_status=?,duration_ms=?,next_attempt_at=?
                        WHERE delivery_id=? AND attempt=?
                        """, result.outcome(), result.status(), duration, next == null ? null : Timestamp.from(next),
                        job.id(), attempt);
                meters.counter("swiftdrop.webhooks.attempts", "outcome", result.outcome()).increment();
            });
        } finally { Arrays.fill(secret, (byte) 0); }
    }

    static long delay(int attempt, long retryAfter) {
        long jittered = Math.round(30 * (1L << Math.min(attempt - 1, 7)) * ThreadLocalRandom.current().nextDouble(.8, 1.2));
        return Math.min(3600, Math.max(jittered, retryAfter));
    }

    record Job(UUID id, UUID eventId, String payload, int attempts, UUID endpointId, String url,
            WebhookSecrets.Encrypted encrypted, boolean active, UUID token) {
        @Override public String toString() { return "Job[id=" + id + "]"; }
    }
}
