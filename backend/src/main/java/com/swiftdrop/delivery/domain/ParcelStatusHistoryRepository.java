package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Parcel status history; entries are only ever added. */
public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistoryEntry, UUID> {

    List<ParcelStatusHistoryEntry> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    List<ParcelStatusHistoryEntry> findByParcelIdAndSequenceBetweenOrderBySequenceAsc(
            UUID parcelId, int fromSequence, int toSequence);

    /**
     * Deliveries and failures per business day of the change (ADM-4, D-08), as rows of
     * {@code [day "YYYY-MM-DD", status "DELIVERED"|"FAILED", count]}. Both statuses are final, so a
     * parcel is counted at most once. {@code start} is inclusive and {@code end} exclusive. Native SQL
     * because JPQL cannot group by a date in another time zone.
     */
    @Query(value = """
            SELECT to_char(occurred_at AT TIME ZONE :zone, 'YYYY-MM-DD') AS day, to_status, COUNT(*) AS total
            FROM parcel_status_history
            WHERE to_status IN ('DELIVERED', 'FAILED')
              AND occurred_at >= :start AND occurred_at < :end
            GROUP BY day, to_status
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> countOutcomesPerDay(@Param("zone") String zone, @Param("start") Instant start,
            @Param("end") Instant end);
}
