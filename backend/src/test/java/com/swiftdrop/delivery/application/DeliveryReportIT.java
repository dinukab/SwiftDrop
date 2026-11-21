package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.application.DeliveryReportService.DailyCount;
import com.swiftdrop.delivery.application.DeliveryReportService.DailyDeliveries;
import com.swiftdrop.delivery.application.DeliveryReportService.DailyOutcome;
import com.swiftdrop.delivery.application.DeliveryReportService.DeliveryOutcomes;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Both reports against real PostgreSQL: the native queries group by the Colombo day, not the UTC
 * day. Each test uses its own dates in 2020, so parcels from other tests are never counted.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class DeliveryReportIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private ParcelCreation creation;
    @Autowired
    private DeliveryReportService reports;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("150.00"), UUID.randomUUID()));
    }

    @Test
    void parcelsAreCountedOnTheirColomboDayAndEmptyDaysAreZero() {
        // 1 Mar 18:29:59 UTC = 1 Mar 23:59:59 in Colombo; 1 Mar 18:30 UTC = 2 Mar 00:00 in Colombo
        createdAt("ORD-1", "2020-03-01T05:00:00Z");
        createdAt("ORD-2", "2020-03-01T18:29:59Z");
        createdAt("ORD-3", "2020-03-01T18:30:00Z");
        createdAt("ORD-4", "2020-03-03T10:00:00Z");
        // Just outside the range on both sides
        createdAt("ORD-5", "2020-02-29T18:29:59Z");
        createdAt("ORD-6", "2020-03-04T18:30:00Z");

        DailyDeliveries report = reports.dailyDeliveries(LocalDate.parse("2020-03-01"),
                LocalDate.parse("2020-03-04"));

        assertThat(report.days()).containsExactly(
                new DailyCount(LocalDate.parse("2020-03-01"), 2),
                new DailyCount(LocalDate.parse("2020-03-02"), 1),
                new DailyCount(LocalDate.parse("2020-03-03"), 1),
                new DailyCount(LocalDate.parse("2020-03-04"), 0));
        assertThat(report.total()).isEqualTo(4);
    }

    @Test
    void outcomesAreCountedOnTheColomboDayTheParcelFinishedAndCancellationsAreIgnored() {
        // Created long before the range: only the day it finished matters (D-08)
        finished("ORD-D1", "2020-05-01T10:00:00Z", "DELIVERED", "2020-05-01T05:00:00Z");
        finished("ORD-D2", "2020-05-01T10:00:00Z", "DELIVERED", "2020-05-01T18:29:59Z");
        finished("ORD-D3", "2020-05-01T10:00:00Z", "DELIVERED", "2020-05-01T18:30:00Z");
        finished("ORD-F1", "2020-05-01T10:00:00Z", "FAILED", "2020-05-01T12:00:00Z");
        finished("ORD-C1", "2020-05-01T10:00:00Z", "CANCELLED", "2020-05-01T12:00:00Z");
        // Finished just outside the range on both sides
        finished("ORD-D4", "2020-04-01T10:00:00Z", "DELIVERED", "2020-04-30T18:29:59Z");
        finished("ORD-F2", "2020-04-01T10:00:00Z", "FAILED", "2020-05-03T18:30:00Z");

        DeliveryOutcomes report = reports.deliveryOutcomes(LocalDate.parse("2020-05-01"),
                LocalDate.parse("2020-05-03"));

        assertThat(report.days()).containsExactly(
                new DailyOutcome(LocalDate.parse("2020-05-01"), 2, 1),
                new DailyOutcome(LocalDate.parse("2020-05-02"), 1, 0),
                new DailyOutcome(LocalDate.parse("2020-05-03"), 0, 0));
        assertThat(report.delivered()).isEqualTo(3);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.successRate()).isEqualByComparingTo("0.75");
    }

    private UUID createdAt(String reference, String instant) {
        UUID parcelId = creation.createMerchantParcel(new NewMerchantParcel(merchantId, reference, contact("Colombo"),
                contact("Kandy"), ParcelSize.SMALL, 1000, null)).parcelId();
        jdbc.update("UPDATE parcels SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse(instant)), parcelId);
        return parcelId;
    }

    /**
     * A parcel whose history ends in {@code status} at {@code finishedAt}. The row is written directly
     * because the report reads only the history; walking the full lifecycle would add nothing here.
     */
    private void finished(String reference, String createdAt, String status, String finishedAt) {
        UUID parcelId = createdAt(reference, createdAt);
        boolean cancelled = status.equals("CANCELLED");
        jdbc.update("""
                INSERT INTO parcel_status_history (id, parcel_id, sequence, from_status, to_status, actor_role,
                    failure_reason_code, event_id, occurred_at)
                VALUES (?, ?, 2, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), parcelId, cancelled ? "CREATED" : "OUT_FOR_DELIVERY", status,
                cancelled ? "MERCHANT" : "COURIER", status.equals("FAILED") ? "RECIPIENT_UNAVAILABLE" : null,
                UUID.randomUUID(), Timestamp.from(Instant.parse(finishedAt)));
    }

    private static DeliveryContact contact(String city) {
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, UUID.randomUUID(), null);
    }
}
