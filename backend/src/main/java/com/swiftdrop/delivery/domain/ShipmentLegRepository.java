package com.swiftdrop.delivery.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftdrop.delivery.api.ParcelStatus;

/** Persistence access for shipment legs (ADR 0007). */
public interface ShipmentLegRepository extends JpaRepository<ShipmentLeg, UUID> {

    /** All legs for a parcel, ordered by sequence. */
    List<ShipmentLeg> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    boolean existsByParcelId(UUID parcelId);

    Optional<ShipmentLeg> findByIdAndParcelId(UUID id, UUID parcelId);

    /** Legs assigned to a courier user. */
    List<ShipmentLeg> findByAssignedCourierIdOrderByCreatedAtAsc(UUID courierId);

    /**
     * Legs that wait for a courier at a hub: not the pickup leg, no courier yet, the previous leg complete and the
     * parcel waiting at a hub. Oldest first.
     */
    @Query("""
            SELECT l FROM ShipmentLeg l, Parcel p
            WHERE p.id = l.parcelId
              AND l.sourceHubId = :hubId
              AND l.assignedCourierId IS NULL
              AND l.sequence > 1
              AND p.status IN :atHub
              AND EXISTS (SELECT 1 FROM ShipmentLeg prev
                          WHERE prev.parcelId = l.parcelId
                            AND prev.sequence = l.sequence - 1
                            AND prev.completedAt IS NOT NULL)
            ORDER BY l.createdAt ASC, l.sequence ASC
            """)
    Page<ShipmentLeg> findWaiting(@Param("hubId") UUID hubId, @Param("atHub") Collection<ParcelStatus> atHub,
            Pageable pageable);
}
