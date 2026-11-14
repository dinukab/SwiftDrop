package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class WebhookWorkerTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    WebhookHttpSender sender = mock(WebhookHttpSender.class);
    WebhookSecrets secrets = new WebhookSecrets("test", "test:" + Base64.getEncoder().encodeToString(new byte[32]));
    WebhookWorker worker;
    UUID endpoint = UUID.randomUUID();

    @BeforeEach void setup() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        worker = new WebhookWorker(jdbc, new TransactionTemplate(manager), secrets, sender,
                Clock.systemUTC(), new SimpleMeterRegistry());
    }

    WebhookWorker.Job job(int attempts, boolean active) {
        return new WebhookWorker.Job(UUID.randomUUID(), UUID.randomUUID(), "{}", attempts, endpoint,
                "https://merchant.example", secrets.encrypt(endpoint, secrets.generate()), active, UUID.randomUUID());
    }

    @Test void unavailableSecretDefersWithoutAttemptOrHttp() {
        var good = job(0, true);
        var job = new WebhookWorker.Job(good.id(), good.eventId(), good.payload(), 0, endpoint, good.url(),
                new WebhookSecrets.Encrypted("missing", new byte[12], new byte[48]), true, good.token());
        worker.send(job);
        verifyNoInteractions(sender);
        verify(jdbc).update(contains("state='SECRET_UNAVAILABLE'"), any(), eq(job.id()), eq(job.token()));
        verify(jdbc, never()).update(contains("INSERT INTO merchant_webhook_attempts"), any(), any(), any(), any());
    }

    @Test void disabledAndExhaustedJobsNeverSend() {
        worker.send(job(0, false));
        worker.send(job(8, true));
        verifyNoInteractions(sender);
        verify(jdbc, times(2)).update(contains("state='FAILED'"), any(UUID.class), any(UUID.class));
    }

    @Test void staleLeaseCannotSendOrRecordAnAttempt() {
        var job = job(0, true);
        when(jdbc.update(contains("SET attempts=?"), any(), any(), any())).thenReturn(0);
        worker.send(job);
        verifyNoInteractions(sender);
        verify(jdbc, never()).update(contains("INSERT INTO merchant_webhook_attempts"), any(), any(), any(), any());
    }

    @Test void successRecordsBoundedMetadataAndPreservesOriginalPayload() {
        var job = job(0, true);
        when(jdbc.update(contains("SET attempts=?"), any(), any(), any())).thenReturn(1);
        when(jdbc.update(contains("SET state=?"), any(), any(), any(), any())).thenReturn(1);
        when(sender.send(eq(job.url()), eq(job.eventId()), eq("{}"), any())).thenReturn(
                new WebhookHttpSender.Result("SUCCESS", 204, false, 0));
        worker.send(job);
        verify(jdbc).update(contains("SET state=?"), eq("SUCCEEDED"), any(), eq(job.id()), eq(job.token()));
        verify(jdbc).update(contains("SET outcome=?"), eq("SUCCESS"), eq(204), anyLong(), isNull(), eq(job.id()), eq(1));
    }

    @Test void backoffHasJitterAndHonorsRetryAfterWithinCap() {
        for (int i = 0; i < 100; i++) {
            assertThat(WebhookWorker.delay(1, 0)).isBetween(24L, 36L);
            assertThat(WebhookWorker.delay(2, 0)).isBetween(48L, 72L);
        }
        assertThat(WebhookWorker.delay(1, 4000)).isEqualTo(3600);
        assertThat(WebhookWorker.delay(1, 300)).isEqualTo(300);
    }
}
