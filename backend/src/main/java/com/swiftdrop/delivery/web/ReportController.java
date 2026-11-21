package com.swiftdrop.delivery.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.application.DeliveryReportService;

/** Admin reports (ADM-4, D-08). Both dates are optional and inclusive; see {@link DeliveryReportService}. */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('ADMIN')")
class ReportController {

    private final DeliveryReportService reports;

    ReportController(DeliveryReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/daily-deliveries")
    DailyDeliveriesResponse dailyDeliveries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return DailyDeliveriesResponse.from(reports.dailyDeliveries(from, to));
    }
}
