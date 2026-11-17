package com.swiftdrop.delivery.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for shipment legs. */
public interface ShipmentLegRepository extends JpaRepository<ShipmentLeg, UUID> {

    /** All legs for a parcel, ordered by sequence. */
    List<ShipmentLeg> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    /** Active (unassigned or assigned) legs for a parcel. */
    List<ShipmentLeg> findByParcelIdAndAssignedCourierIdIsNotNullOrderBySequenceAsc(UUID parcelId);

    /** Legs assigned to a courier. */
    List<ShipmentLeg> findByAssignedCourierIdOrderByCreatedAtAsc(UUID courierId);
}
