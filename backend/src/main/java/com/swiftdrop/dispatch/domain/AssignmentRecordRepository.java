package com.swiftdrop.dispatch.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for assignment history; restricted to the {@code dispatch} module (ADR 0003). */
public interface AssignmentRecordRepository extends JpaRepository<AssignmentRecord, UUID> {

    /** The current assignment of one leg (by its sequence number) of a parcel, if any. */
    Optional<AssignmentRecord> findByParcelIdAndLegSequenceAndEndedAtIsNull(UUID parcelId, int legSequence);

    /** Every assignment of a parcel, oldest first. */
    List<AssignmentRecord> findByParcelIdOrderByAssignedAtAscIdAsc(UUID parcelId);
}
