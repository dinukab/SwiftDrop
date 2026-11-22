package com.swiftdrop.delivery.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Outbox rows; written with status changes, published by the relay (ADR 0004 §3). */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    /**
     * The oldest unpublished events, locked for this relay run. Read in identity order so events
     * leave in the order they were written. Must be called inside a transaction.
     */
    @Query(value = "SELECT * FROM parcel_outbox_events WHERE published_at IS NULL "
            + "ORDER BY id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockUnpublished(@Param("limit") int limit);
}
