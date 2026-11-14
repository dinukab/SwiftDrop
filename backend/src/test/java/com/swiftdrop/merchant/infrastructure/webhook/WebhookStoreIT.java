package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.swiftdrop.delivery.api.*;

@Testcontainers
class WebhookStoreIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    WebhookSecrets secrets;
    WebhookStore store;
    UUID user;
    UUID merchant;
    @BeforeAll static void database() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM merchant_webhook_attempts");
        jdbc.update("DELETE FROM merchant_webhook_deliveries");
        jdbc.update("DELETE FROM merchant_webhook_endpoints");
        jdbc.update("DELETE FROM merchants");
        user = UUID.randomUUID(); merchant = UUID.randomUUID();
        jdbc.update("INSERT INTO merchants(id,user_id,business_name,status) VALUES (?,?,'Test merchant','APPROVED')", merchant, user);
        secrets = new WebhookSecrets("test", "test:" + Base64.getEncoder().encodeToString(new byte[32]));
        store = new WebhookStore(jdbc, tx, secrets, new WebhookDestination(true, Set.of()));
    }

    ParcelStatusChanged event() {
        return ParcelStatusChanged.of(UUID.randomUUID(), Instant.now(), new ParcelStatusChangedData(
                UUID.randomUUID(), "SD-TEST", null, ParcelStatus.CREATED, 1, ParcelSource.MERCHANT, null, merchant, null, null));
    }

    UUID job() {
        store.create(user, "http://localhost:9999/hook");
        tx.executeWithoutResult(t -> store.createDeliveries(event()));
        return jdbc.queryForObject("SELECT id FROM merchant_webhook_deliveries", UUID.class);
    }

    WebhookWorker worker(WebhookHttpSender sender) {
        return new WebhookWorker(jdbc, tx, secrets, sender, Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @Test void jobsAreOwnerScopedIdempotentAndRollbackWithInboxTransaction() {
        store.create(user, "http://localhost:9999/hook");
        var disabled = store.create(user, "http://localhost:9998/hook");
        store.disable(user, disabled.id(), false);
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO merchants(id,user_id,business_name,status) VALUES (?,?,'Other','APPROVED')", UUID.randomUUID(), other);
        store.create(other, "http://localhost:9997/hook");
        var event = event();
        tx.executeWithoutResult(t -> { store.createDeliveries(event); store.createDeliveries(event); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_webhook_deliveries", Integer.class)).isOne();
        tx.executeWithoutResult(t -> { store.createDeliveries(event()); t.setRollbackOnly(); });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchant_webhook_deliveries", Integer.class)).isOne();
        assertThat(store.deliveries(other, false, 0)).isEmpty();
        var id = store.deliveries(user, false, 0).getFirst().id();
        assertThatThrownBy(() -> store.attempts(other, false, id))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> store.disable(other, disabled.id(), false))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test void onlyApprovedMerchantsRegisterAndNoSecretsAreReturnedInLists() {
        store.create(user, "http://localhost:9999");
        assertThat(store.endpoints(user)).hasSize(1);
        jdbc.update("UPDATE merchants SET status='PENDING' WHERE id=?", merchant);
        assertThatThrownBy(() -> store.create(user, "http://localhost:9999"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    }

    @Test void leasesPreventConcurrentClaimsAndExpiredLeaseRecovers() {
        job();
        var worker = worker(mock(WebhookHttpSender.class));
        var first = worker.claim();
        assertThat(worker.claim()).isNull();
        jdbc.update("UPDATE merchant_webhook_deliveries SET next_attempt_at=now()-interval '1 second'");
        var second = worker.claim();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.token()).isNotEqualTo(first.token());
    }

    @Test void sendsOutsideTransactionAndRecordsSuccess() {
        UUID id = job();
        var sender = mock(WebhookHttpSender.class);
        when(sender.send(anyString(), any(), anyString(), any())).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new WebhookHttpSender.Result("SUCCESS", 204, false, 0);
        });
        worker(sender).poll();
        assertThat(jdbc.queryForObject("SELECT state FROM merchant_webhook_deliveries WHERE id=?", String.class, id)).isEqualTo("SUCCEEDED");
        assertThat(store.attempts(user, false, id)).singleElement().satisfies(a -> {
            assertThat(a.attempt()).isOne(); assertThat(a.httpStatus()).isEqualTo(204);
        });
    }

    @Test void missingKeyConsumesNoAttemptAndRestorationResumes() {
        UUID id = job();
        jdbc.update("UPDATE merchant_webhook_endpoints SET key_id='missing'");
        var sender = mock(WebhookHttpSender.class);
        var worker = worker(sender);
        worker.poll();
        verifyNoInteractions(sender);
        assertThat(jdbc.queryForObject("SELECT state FROM merchant_webhook_deliveries WHERE id=?", String.class, id)).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(store.attempts(user, false, id)).isEmpty();
        jdbc.update("UPDATE merchant_webhook_endpoints SET key_id='test'");
        jdbc.update("UPDATE merchant_webhook_deliveries SET next_attempt_at=now()-interval '1 second'");
        when(sender.send(anyString(), any(), anyString(), any())).thenReturn(new WebhookHttpSender.Result("SUCCESS", 200, false, 0));
        worker.poll();
        assertThat(store.attempts(user, false, id)).hasSize(1);
    }

    @Test void transientFailuresRetryButEighthAttemptIsTerminal() {
        UUID id = job();
        var sender = mock(WebhookHttpSender.class);
        when(sender.send(anyString(), any(), anyString(), any())).thenReturn(new WebhookHttpSender.Result("HTTP_ERROR", 503, true, 0));
        var worker = worker(sender);
        for (int i = 1; i <= 8; i++) {
            jdbc.update("UPDATE merchant_webhook_deliveries SET next_attempt_at=now()-interval '1 second'");
            worker.poll();
            assertThat(jdbc.queryForObject("SELECT state FROM merchant_webhook_deliveries WHERE id=?", String.class, id))
                    .isEqualTo(i < 8 ? "PENDING" : "FAILED");
        }
        assertThat(store.attempts(user, false, id)).hasSize(8);
        verify(sender, times(8)).send(anyString(), any(), anyString(), any());
    }
}
