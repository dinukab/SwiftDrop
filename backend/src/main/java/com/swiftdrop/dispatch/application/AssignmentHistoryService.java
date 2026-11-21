package com.swiftdrop.dispatch.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.dispatch.domain.AssignmentRecord;
import com.swiftdrop.dispatch.domain.AssignmentRecordRepository;
import com.swiftdrop.delivery.api.LegType;

/**
 * Assignment history: one row per assignment, at most one active row per parcel and leg. It runs in
 * the dispatcher's transaction, after {@code delivery} has taken the parcel lock, so two dispatchers
 * assigning one parcel are serialized and the database's partial unique index is a last guard, not
 * the mechanism.
 */
@Service
@Lazy
@Transactional(readOnly = true)
public class AssignmentHistoryService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentHistoryService.class);

    private final AssignmentRecordRepository records;
    private final Clock clock;

    @Autowired
    public AssignmentHistoryService(AssignmentRecordRepository records) {
        this(records, Clock.systemUTC());
    }

    AssignmentHistoryService(AssignmentRecordRepository records, Clock clock) {
        this.records = Objects.requireNonNull(records, "records");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Records that {@code courierUserId} now holds the pickup leg of the parcel. Assigning the courier who
     * already holds it changes nothing; assigning another courier ends the previous assignment
     * ({@code REASSIGNED}) and starts a new one.
     *
     * @return the active assignment
     * @throws ParcelConflictException if a concurrent assignment won the race at the database level
     */
    @Transactional
    public AssignmentRecord record(UUID parcelId, UUID courierUserId, UUID dispatcherUserId, UUID hubId) {
        return record(parcelId, LegType.PICKUP, 1, courierUserId, dispatcherUserId, hubId);
    }

    /**
     * Records that {@code courierUserId} now holds leg {@code legSequence} of the parcel (ADR 0007), with the
     * same rules as for the pickup leg: the same courier again changes nothing, another courier ends the
     * previous assignment of <em>that leg</em> and starts a new one. Other legs are not touched.
     *
     * @throws ParcelConflictException if a concurrent assignment won the race at the database level
     */
    @Transactional
    public AssignmentRecord record(UUID parcelId, LegType legType, int legSequence, UUID courierUserId,
            UUID dispatcherUserId, UUID hubId) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(legType, "legType must not be null");
        Objects.requireNonNull(courierUserId, "courierUserId must not be null");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId must not be null");
        Objects.requireNonNull(hubId, "hubId must not be null");

        Optional<AssignmentRecord> active = records.findByParcelIdAndLegSequenceAndEndedAtIsNull(parcelId, legSequence);
        if (active.isPresent() && active.get().getCourierUserId().equals(courierUserId)) {
            return active.get();
        }
        try {
            if (active.isPresent()) {
                active.get().end(AssignmentRecord.EndReason.REASSIGNED, clock.instant());
                // Flushed first: the new active row must not meet the old one in the unique index
                records.saveAndFlush(active.get());
            }
            AssignmentRecord created = records.saveAndFlush(new AssignmentRecord(parcelId, legType, legSequence,
                    courierUserId, dispatcherUserId, hubId, clock.instant()));
            // IDs only: no names in logs
            log.info("Parcel {} leg {} {} assignment: courier {} by dispatcher {}", parcelId, legSequence, legType,
                    courierUserId, dispatcherUserId);
            return created;
        } catch (DataIntegrityViolationException e) {
            throw new ParcelConflictException("The parcel was assigned at the same time; reload and try again");
        }
    }

    /** Every assignment of the parcel, oldest first. */
    public List<AssignmentRecord> history(UUID parcelId) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        return records.findByParcelIdOrderByAssignedAtAscIdAsc(parcelId);
    }
}
