package com.swiftdrop.delivery.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;

/**
 * Admin reports (ADM-4) as defined by D-08. Days are business days in the configured time zone
 * ({@code Asia/Colombo} by default); both ends of a range are included. A missing {@code to} means
 * today; a missing {@code from} means 30 days ending at {@code to}.
 */
@Service
public class DeliveryReportService {

    /** The longest range a report accepts, in days (D-08). */
    public static final int MAX_RANGE_DAYS = 92;
    /** The range used when the caller gives no {@code from}: 30 days ending at {@code to}. */
    static final int DEFAULT_RANGE_DAYS = 30;
    /** Decimal places of {@link DeliveryOutcomes#successRate()}, a fraction between 0 and 1. */
    static final int RATE_SCALE = 4;

    private final ParcelRepository parcels;
    private final ParcelStatusHistoryRepository history;
    private final ZoneId businessZone;
    private final Clock clock;

    @Autowired
    public DeliveryReportService(ParcelRepository parcels, ParcelStatusHistoryRepository history,
            @Value("${swiftdrop.delivery.business-time-zone:Asia/Colombo}") String businessZone) {
        this(parcels, history, ZoneId.of(businessZone), Clock.systemUTC());
    }

    DeliveryReportService(ParcelRepository parcels, ParcelStatusHistoryRepository history, ZoneId businessZone,
            Clock clock) {
        this.parcels = parcels;
        this.history = history;
        this.businessZone = businessZone;
        this.clock = clock;
    }

    /** One business day and the number of parcels created on it. */
    public record DailyCount(LocalDate date, long count) {
    }

    /** Parcels created per day from {@code from} to {@code to}, including days with none. */
    public record DailyDeliveries(LocalDate from, LocalDate to, ZoneId timeZone, long total, List<DailyCount> days) {
    }

    /** Parcels that reached {@code DELIVERED} or {@code FAILED} on one business day. */
    public record DailyOutcome(LocalDate date, long delivered, long failed) {
    }

    /**
     * Delivery outcomes from {@code from} to {@code to}. {@code successRate} is
     * {@code delivered / (delivered + failed)} rounded to 4 places, or {@code null} without outcomes.
     */
    public record DeliveryOutcomes(LocalDate from, LocalDate to, ZoneId timeZone, long delivered, long failed,
            BigDecimal successRate, List<DailyOutcome> days) {
    }

    /**
     * Parcels created per business day, including days with none.
     *
     * @throws InvalidReportRangeException if {@code from} is after {@code to} or the range is longer
     *         than {@value #MAX_RANGE_DAYS} days
     */
    @Transactional(readOnly = true)
    public DailyDeliveries dailyDeliveries(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        Map<LocalDate, Long> counted = new HashMap<>();
        for (Object[] row : parcels.countCreatedPerDay(businessZone.getId(), range.startInstant(),
                range.endInstant())) {
            counted.put(LocalDate.parse((String) row[0]), ((Number) row[1]).longValue());
        }

        List<DailyCount> days = new ArrayList<>();
        long total = 0;
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            long count = counted.getOrDefault(day, 0L);
            days.add(new DailyCount(day, count));
            total += count;
        }
        return new DailyDeliveries(range.from(), range.to(), businessZone, total, List.copyOf(days));
    }

    /**
     * Deliveries and failures counted by the business day the parcel reached that final status
     * (D-08), so a parcel created before the range but finished in it counts. Cancelled and
     * unfinished parcels are not outcomes.
     *
     * @throws InvalidReportRangeException if {@code from} is after {@code to} or the range is longer
     *         than {@value #MAX_RANGE_DAYS} days
     */
    @Transactional(readOnly = true)
    public DeliveryOutcomes deliveryOutcomes(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        Map<LocalDate, long[]> counted = new HashMap<>();
        for (Object[] row : history.countOutcomesPerDay(businessZone.getId(), range.startInstant(),
                range.endInstant())) {
            long[] pair = counted.computeIfAbsent(LocalDate.parse((String) row[0]), day -> new long[2]);
            int index = ParcelStatus.valueOf((String) row[1]) == ParcelStatus.DELIVERED ? 0 : 1;
            pair[index] += ((Number) row[2]).longValue();
        }

        List<DailyOutcome> days = new ArrayList<>();
        long delivered = 0;
        long failed = 0;
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            long[] pair = counted.getOrDefault(day, new long[2]);
            days.add(new DailyOutcome(day, pair[0], pair[1]));
            delivered += pair[0];
            failed += pair[1];
        }
        return new DeliveryOutcomes(range.from(), range.to(), businessZone, delivered, failed,
                successRate(delivered, failed), List.copyOf(days));
    }

    static BigDecimal successRate(long delivered, long failed) {
        long finished = delivered + failed;
        if (finished == 0) {
            return null;
        }
        return BigDecimal.valueOf(delivered).divide(BigDecimal.valueOf(finished), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** Inclusive business days and the matching half-open instant interval. */
    private record Range(LocalDate from, LocalDate to, Instant startInstant, Instant endInstant) {
    }

    private Range range(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(businessZone));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1);
        if (start.isAfter(end)) {
            throw new InvalidReportRangeException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidReportRangeException("The date range must be at most " + MAX_RANGE_DAYS + " days");
        }
        return new Range(start, end, start.atStartOfDay(businessZone).toInstant(),
                end.plusDays(1).atStartOfDay(businessZone).toInstant());
    }
}
