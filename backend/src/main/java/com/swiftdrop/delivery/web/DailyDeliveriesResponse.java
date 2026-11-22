package com.swiftdrop.delivery.web;

import java.time.LocalDate;
import java.util.List;

import com.swiftdrop.delivery.application.DeliveryReportService.DailyDeliveries;

/** Parcels created per business day (ADM-4); dates are ISO {@code YYYY-MM-DD} in {@code timeZone}. */
record DailyDeliveriesResponse(LocalDate from, LocalDate to, String timeZone, long total, List<Day> days) {

    record Day(LocalDate date, long count) {
    }

    static DailyDeliveriesResponse from(DailyDeliveries report) {
        return new DailyDeliveriesResponse(report.from(), report.to(), report.timeZone().getId(), report.total(),
                report.days().stream().map(day -> new Day(day.date(), day.count())).toList());
    }
}
