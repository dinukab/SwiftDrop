package com.swiftdrop.delivery.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.swiftdrop.delivery.application.DeliveryReportService.DeliveryOutcomes;

/**
 * Delivery outcomes per business day (ADM-4, D-08). {@code successRate} is a fraction between 0 and 1
 * with 4 decimal places, or {@code null} when nothing was delivered or failed in the range.
 */
record DeliveryOutcomesResponse(LocalDate from, LocalDate to, String timeZone, long delivered, long failed,
        BigDecimal successRate, List<Day> days) {

    record Day(LocalDate date, long delivered, long failed) {
    }

    static DeliveryOutcomesResponse from(DeliveryOutcomes report) {
        return new DeliveryOutcomesResponse(report.from(), report.to(), report.timeZone().getId(),
                report.delivered(), report.failed(), report.successRate(),
                report.days().stream().map(day -> new Day(day.date(), day.delivered(), day.failed())).toList());
    }
}
