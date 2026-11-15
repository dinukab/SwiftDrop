package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class WebhookRedeliveryTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ResultSet row = mock(ResultSet.class);
    WebhookStore store;
    UUID user = UUID.randomUUID(), merchant = UUID.randomUUID(), delivery = UUID.randomUUID();

    @BeforeEach void setup() throws Exception {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        store = new WebhookStore(jdbc, new TransactionTemplate(manager), mock(WebhookSecrets.class),
                mock(WebhookDestination.class));
        when(jdbc.queryForList(anyString(), eq(UUID.class), eq(user))).thenReturn(List.of(merchant));
        when(row.getString("state")).thenReturn("FAILED");
        when(row.getString("merchant_status")).thenReturn("APPROVED");
        when(row.getInt("attempts")).thenReturn(8);
        when(row.getInt("redelivery_count")).thenReturn(0);
        when(row.getBoolean("active")).thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
    }

    @Test void ownerScopedRequestIsAuditedAndStartsNewSeriesWithoutDeletingAttempts() {
        store.redeliver(user, false, delivery);
        verify(jdbc).query(contains("AND e.merchant_id=?"), any(RowMapper.class), eq(new Object[] {delivery, merchant}));
        verify(jdbc).update(contains("INSERT INTO merchant_webhook_redeliveries"),
                eq(delivery), eq(1), eq(user), eq("MERCHANT"), eq(9));
        verify(jdbc).update(contains("series_attempts=0"), eq(delivery));
        verify(jdbc, never()).update(contains("DELETE"), any(Object[].class));
    }

    @Test void capActiveSeriesAndDisabledEndpointAreRejected() throws Exception {
        when(row.getInt("redelivery_count")).thenReturn(3);
        assertConflict();
        when(row.getInt("redelivery_count")).thenReturn(0);
        for (String state : List.of("PENDING", "SENDING", "SUCCEEDED", "SECRET_UNAVAILABLE")) {
            when(row.getString("state")).thenReturn(state);
            assertConflict();
        }
        when(row.getString("state")).thenReturn("FAILED");
        when(row.getBoolean("active")).thenReturn(false);
        assertConflict();
        when(row.getBoolean("active")).thenReturn(true);
        when(row.getString("merchant_status")).thenReturn("SUSPENDED");
        assertConflict();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void anotherOwnersRecordReturns404AndIsNotAudited() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThatThrownBy(() -> store.redeliver(user, false, delivery))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private void assertConflict() {
        assertThatThrownBy(() -> store.redeliver(user, false, delivery))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
}
