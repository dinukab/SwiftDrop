package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.application.DeliveryReportService.DailyCount;
import com.swiftdrop.delivery.application.DeliveryReportService.DailyDeliveries;
import com.swiftdrop.delivery.application.DeliveryReportService.DailyOutcome;
import com.swiftdrop.delivery.application.DeliveryReportService.DeliveryOutcomes;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;

class DeliveryReportServiceTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final ParcelStatusHistoryRepository history = mock(ParcelStatusHistoryRepository.class);

    @Test
    void rangeCoversWholeColomboDaysNotUtcDays() {
        DeliveryReportService service = at("2026-11-10T10:00:00Z");

        service.dailyDeliveries(LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-04"));

        // Colombo is UTC+05:30: 3 Nov 00:00 there is 2 Nov 18:30 UTC; the end is exclusive
        verify(parcels).countCreatedPerDay("Asia/Colombo", Instant.parse("2026-11-02T18:30:00Z"),
                Instant.parse("2026-11-04T18:30:00Z"));
    }

    @Test
    void everyDayIsListedAndDaysWithoutParcelsCountZero() {
        DeliveryReportService service = at("2026-11-10T10:00:00Z");
        when(parcels.countCreatedPerDay(anyString(), any(), any())).thenReturn(List.of(
                new Object[] {"2026-11-01", 3L},
                new Object[] {"2026-11-03", 2L}));

        DailyDeliveries report = service.dailyDeliveries(LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-04"));

        assertThat(report.days()).containsExactly(
                new DailyCount(LocalDate.parse("2026-11-01"), 3),
                new DailyCount(LocalDate.parse("2026-11-02"), 0),
                new DailyCount(LocalDate.parse("2026-11-03"), 2),
                new DailyCount(LocalDate.parse("2026-11-04"), 0));
        assertThat(report.total()).isEqualTo(5);
        assertThat(report.timeZone()).isEqualTo(COLOMBO);
    }

    @Test
    void aSingleDayRangeIsAllowed() {
        DeliveryReportService service = at("2026-11-10T10:00:00Z");

        DailyDeliveries report = service.dailyDeliveries(LocalDate.parse("2026-11-05"),
                LocalDate.parse("2026-11-05"));

        assertThat(report.days()).containsExactly(new DailyCount(LocalDate.parse("2026-11-05"), 0));
        assertThat(report.total()).isZero();
    }

    @Test
    void withoutDatesTheLast30DaysEndingTodayInColomboAreUsed() {
        // 20 Nov 20:00 UTC is already 21 Nov 01:30 in Colombo
        DeliveryReportService service = at("2026-11-20T20:00:00Z");

        DailyDeliveries report = service.dailyDeliveries(null, null);

        assertThat(report.to()).isEqualTo(LocalDate.parse("2026-11-21"));
        assertThat(report.from()).isEqualTo(LocalDate.parse("2026-10-23"));
        assertThat(report.days()).hasSize(30);
    }

    @Test
    void onlyToGivenMeans30DaysEndingThere() {
        DeliveryReportService service = at("2026-11-20T10:00:00Z");

        DailyDeliveries report = service.dailyDeliveries(null, LocalDate.parse("2026-10-31"));

        assertThat(report.from()).isEqualTo(LocalDate.parse("2026-10-02"));
        assertThat(report.days()).hasSize(30);
    }

    @Test
    void ninetyTwoDaysAreAllowed() {
        DeliveryReportService service = at("2026-11-20T10:00:00Z");
        LocalDate from = LocalDate.parse("2026-08-01");

        DailyDeliveries report = service.dailyDeliveries(from, from.plusDays(91));

        assertThat(report.days()).hasSize(92);
    }

    @Test
    void ninetyThreeDaysAreRejectedWithoutQuerying() {
        DeliveryReportService service = at("2026-11-20T10:00:00Z");
        LocalDate from = LocalDate.parse("2026-08-01");

        assertThatThrownBy(() -> service.dailyDeliveries(from, from.plusDays(92)))
                .isInstanceOf(InvalidReportRangeException.class)
                .hasMessage("The date range must be at most 92 days");
        verifyNoInteractions(parcels);
    }

    @Test
    void fromAfterToIsRejectedWithoutQuerying() {
        DeliveryReportService service = at("2026-11-20T10:00:00Z");

        assertThatThrownBy(() -> service.dailyDeliveries(LocalDate.parse("2026-11-05"),
                LocalDate.parse("2026-11-04")))
                .isInstanceOf(InvalidReportRangeException.class)
                .hasMessage("'from' must not be after 'to'");
        verifyNoInteractions(parcels);
    }

    @Test
    void onlyFromGivenAfterTodayIsRejected() {
        DeliveryReportService service = at("2026-11-20T10:00:00Z");

        assertThatThrownBy(() -> service.dailyDeliveries(LocalDate.parse("2026-12-01"), null))
                .isInstanceOf(InvalidReportRangeException.class);
    }

    @Test
    void outcomesUseTheSameColomboRangeAndFillEveryDay() {
        DeliveryReportService service = at("2026-11-10T10:00:00Z");
        when(history.countOutcomesPerDay(anyString(), any(), any())).thenReturn(List.of(
                new Object[] {"2026-11-01", "DELIVERED", 3L},
                new Object[] {"2026-11-01", "FAILED", 1L},
                new Object[] {"2026-11-03", "DELIVERED", 4L}));

        DeliveryOutcomes report = service.deliveryOutcomes(LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-03"));

        verify(history).countOutcomesPerDay("Asia/Colombo", Instant.parse("2026-10-31T18:30:00Z"),
                Instant.parse("2026-11-03T18:30:00Z"));
        assertThat(report.days()).containsExactly(
                new DailyOutcome(LocalDate.parse("2026-11-01"), 3, 1),
                new DailyOutcome(LocalDate.parse("2026-11-02"), 0, 0),
                new DailyOutcome(LocalDate.parse("2026-11-03"), 4, 0));
        assertThat(report.delivered()).isEqualTo(7);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.successRate()).isEqualByComparingTo("0.875");
    }

    @Test
    void successRateIsNullWithoutOutcomes() {
        DeliveryReportService service = at("2026-11-10T10:00:00Z");

        DeliveryOutcomes report = service.deliveryOutcomes(LocalDate.parse("2026-11-01"),
                LocalDate.parse("2026-11-02"));

        assertThat(report.delivered()).isZero();
        assertThat(report.failed()).isZero();
        assertThat(report.successRate()).isNull();
        assertThat(report.days()).hasSize(2);
    }

    @Test
    void successRateHasFourPlacesRoundedHalfUp() {
        assertThat(DeliveryReportService.successRate(2, 1)).isEqualTo(new BigDecimal("0.6667"));
        assertThat(DeliveryReportService.successRate(1, 2)).isEqualTo(new BigDecimal("0.3333"));
        assertThat(DeliveryReportService.successRate(5, 0)).isEqualTo(new BigDecimal("1.0000"));
        assertThat(DeliveryReportService.successRate(0, 3)).isEqualTo(new BigDecimal("0.0000"));
    }

    @Test
    void outcomesRejectInvalidRangesWithoutQuerying() {
        DeliveryReportService service = at("2026-11-20T10:00:00Z");
        LocalDate from = LocalDate.parse("2026-08-01");

        assertThatThrownBy(() -> service.deliveryOutcomes(from, from.plusDays(92)))
                .isInstanceOf(InvalidReportRangeException.class);
        assertThatThrownBy(() -> service.deliveryOutcomes(from.plusDays(1), from))
                .isInstanceOf(InvalidReportRangeException.class);
        verifyNoInteractions(history);
    }

    @Test
    void outcomesDefaultToTheLast30Days() {
        DeliveryReportService service = at("2026-11-20T20:00:00Z");

        DeliveryOutcomes report = service.deliveryOutcomes(null, null);

        assertThat(report.from()).isEqualTo(LocalDate.parse("2026-10-23"));
        assertThat(report.to()).isEqualTo(LocalDate.parse("2026-11-21"));
    }

    private DeliveryReportService at(String now) {
        return new DeliveryReportService(parcels, history, COLOMBO, Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }
}
