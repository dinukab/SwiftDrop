package com.swiftdrop.dispatch.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for assignment history; restricted to the {@code dispatch} module (ADR 0003). */
public interface AssignmentRecordRepository extends JpaRepository<AssignmentRecord, UUID> {

    /** The current assignment of a parcel's leg, if any. */
    Optional<AssignmentRecord> findByParcelIdAndLegTypeAndEndedAtIsNull(UUID parcelId, LegType legType);

    /** Every assignment of a parcel, oldest first. */
    List<AssignmentRecord> findByParcelIdOrderByAssignedAtAscIdAsc(UUID parcelId);
}
