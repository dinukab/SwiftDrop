package com.swiftdrop.delivery.application;

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

import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * Admin reports (ADM-4) as defined by D-08. Days are business days in the configured time zone
 * ({@code Asia/Colombo} by default); both ends of a range are included.
 */
@Service
public class DeliveryReportService {

    /** The longest range a report accepts, in days (D-08). */
    public static final int MAX_RANGE_DAYS = 92;
    /** The range used when the caller gives no {@code from}: 30 days ending at {@code to}. */
    static final int DEFAULT_RANGE_DAYS = 30;

    private final ParcelRepository parcels;
    private final ZoneId businessZone;
    private final Clock clock;

    @Autowired
    public DeliveryReportService(ParcelRepository parcels,
            @Value("${swiftdrop.delivery.business-time-zone:Asia/Colombo}") String businessZone) {
        this(parcels, ZoneId.of(businessZone), Clock.systemUTC());
    }

    DeliveryReportService(ParcelRepository parcels, ZoneId businessZone, Clock clock) {
        this.parcels = parcels;
        this.businessZone = businessZone;
        this.clock = clock;
    }

    /** One business day and the number of parcels created on it. */
    public record DailyCount(LocalDate date, long count) {
    }

    /** Parcels created per day from {@code from} to {@code to}, including days with none. */
    public record DailyDeliveries(LocalDate from, LocalDate to, ZoneId timeZone, long total, List<DailyCount> days) {
    }

    /**
     * Parcels created per business day. A missing {@code to} means today; a missing {@code from}
     * means 30 days ending at {@code to}.
     *
     * @throws InvalidReportRangeException if {@code from} is after {@code to} or the range is longer
     *         than {@value #MAX_RANGE_DAYS} days
     */
    @Transactional(readOnly = true)
    public DailyDeliveries dailyDeliveries(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(businessZone));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS - 1);
        checkRange(start, end);

        Instant startInstant = start.atStartOfDay(businessZone).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(businessZone).toInstant();
        Map<LocalDate, Long> counted = new HashMap<>();
        for (Object[] row : parcels.countCreatedPerDay(businessZone.getId(), startInstant, endInstant)) {
            counted.put(LocalDate.parse((String) row[0]), ((Number) row[1]).longValue());
        }

        List<DailyCount> days = new ArrayList<>();
        long total = 0;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            long count = counted.getOrDefault(day, 0L);
            days.add(new DailyCount(day, count));
            total += count;
        }
        return new DailyDeliveries(start, end, businessZone, total, List.copyOf(days));
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidReportRangeException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidReportRangeException("The date range must be at most " + MAX_RANGE_DAYS + " days");
        }
    }
}
