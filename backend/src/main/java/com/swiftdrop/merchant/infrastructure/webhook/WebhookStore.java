package com.swiftdrop.merchant.infrastructure.webhook;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.merchant.infrastructure.messaging.MerchantWebhookEffect;

/** All owner predicates are derived from the authenticated user's merchant row. */
public final class WebhookStore implements MerchantWebhookEffect {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final WebhookSecrets secrets;
    private final WebhookDestination destination;
    private final JsonMapper mapper = JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    public WebhookStore(JdbcTemplate jdbc, TransactionTemplate transactions,
            WebhookSecrets secrets, WebhookDestination destination) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.secrets = secrets;
        this.destination = destination;
    }

    public Created create(UUID userId, String url) {
        UUID merchant = owner(userId);
        try { destination.validate(url); }
        catch (WebhookDestination.Refused ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook destination refused");
        }
        return transactions.execute(tx -> {
            // Lock also serializes endpoint registration against approval changes.
            var status = jdbc.queryForObject("SELECT status FROM merchants WHERE id=? FOR UPDATE", String.class, merchant);
            if (!"APPROVED".equals(status)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant approval required");
            UUID id = UUID.randomUUID();
            byte[] secret = secrets.generate();
            try {
                var encrypted = secrets.encrypt(id, secret);
                jdbc.update("""
                        INSERT INTO merchant_webhook_endpoints(id,merchant_id,url,key_id,nonce,ciphertext)
                        VALUES (?,?,?,?,?,?)
                        """, id, merchant, url, encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext());
                return new Created(id, url, java.util.Base64.getEncoder().encodeToString(secret));
            } finally { java.util.Arrays.fill(secret, (byte) 0); }
        });
    }

    public List<Endpoint> endpoints(UUID userId) {
        return jdbc.query("SELECT id,url,active FROM merchant_webhook_endpoints WHERE merchant_id=? ORDER BY created_at",
                (rs, n) -> new Endpoint(rs.getObject("id", UUID.class), rs.getString("url"), rs.getBoolean("active")), owner(userId));
    }

    public void disable(UUID userId, UUID endpoint, boolean admin) {
        int count = admin
                ? jdbc.update("UPDATE merchant_webhook_endpoints SET active=false WHERE id=?", endpoint)
                : jdbc.update("UPDATE merchant_webhook_endpoints SET active=false WHERE id=? AND merchant_id=?", endpoint, owner(userId));
        if (count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public List<Delivery> deliveries(UUID userId, boolean admin, int page) {
        String predicate = admin ? "" : " AND e.merchant_id=?";
        Object[] args = admin ? new Object[] {page * 50L} : new Object[] {owner(userId), page * 50L};
        return jdbc.query("""
                SELECT d.id,d.event_id,d.endpoint_id,d.state,d.attempts FROM merchant_webhook_deliveries d
                JOIN merchant_webhook_endpoints e ON e.id=d.endpoint_id WHERE true
                """ + predicate + " ORDER BY d.created_at DESC,d.id LIMIT 50 OFFSET ?",
                (rs, n) -> new Delivery(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                        rs.getObject("endpoint_id", UUID.class), rs.getString("state"), rs.getInt("attempts")), args);
    }

    public List<Attempt> attempts(UUID userId, boolean admin, UUID delivery) {
        String predicate = admin ? "" : " AND e.merchant_id=?";
        Object[] args = admin ? new Object[] {delivery} : new Object[] {delivery, owner(userId)};
        var found = jdbc.queryForList("""
                SELECT d.id FROM merchant_webhook_deliveries d
                JOIN merchant_webhook_endpoints e ON e.id=d.endpoint_id WHERE d.id=?
                """ + predicate, UUID.class, args);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return jdbc.query("""
                SELECT attempt,attempted_at,outcome,http_status,duration_ms,next_attempt_at
                FROM merchant_webhook_attempts WHERE delivery_id=? ORDER BY attempt
                """, (rs, n) -> new Attempt(rs.getInt(1), rs.getTimestamp(2).toInstant(),
                        rs.getString(3), rs.getObject(4, Integer.class), rs.getLong(5),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()), delivery);
    }

    private UUID owner(UUID userId) {
        var ids = jdbc.queryForList("SELECT id FROM merchants WHERE user_id=?", UUID.class, userId);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ids.getFirst();
    }

    @Override public boolean createDeliveries(ParcelStatusChanged event) {
        if (event.data().source() != ParcelSource.MERCHANT) return true;
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Webhook jobs require the inbox transaction");
        }
        String payload = mapper.writeValueAsString(event);
        jdbc.update("""
                INSERT INTO merchant_webhook_deliveries(id,endpoint_id,event_id,payload)
                SELECT gen_random_uuid(),id,?,? FROM merchant_webhook_endpoints
                WHERE merchant_id=? AND active=true
                ON CONFLICT (event_id,endpoint_id) DO NOTHING
                """, event.eventId(), payload, event.data().ownerMerchantId());
        return true;
    }

    public record Created(UUID id, String url, String secret) {
        @Override public String toString() { return "Created[id=" + id + "]"; }
    }
    public record Endpoint(UUID id, String url, boolean active) {
        @Override public String toString() { return "Endpoint[id=" + id + ", active=" + active + "]"; }
    }
    public record Delivery(UUID id, UUID eventId, UUID endpointId, String state, int attempts) {}
    public record Attempt(int attempt, java.time.Instant attemptedAt, String outcome, Integer httpStatus,
            long durationMs, java.time.Instant nextAttemptAt) {}
}
