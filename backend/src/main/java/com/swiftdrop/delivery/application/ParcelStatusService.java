package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * Courier status changes (COU-3, COU-4) under the rules of ADR 0006: lock the parcel, check the
 * rule, update it, and record history and outbox event, all in one transaction.
 */
@Service
public class ParcelStatusService {

    private final ParcelRepository parcels;
    private final DeliveryProofRepository proofs;
    private final ParcelEventRecorder recorder;
    private final ObjectFactory<LegProgress> legs;
    private final Clock clock;

    @Autowired
    public ParcelStatusService(ParcelRepository parcels, DeliveryProofRepository proofs, ParcelEventRecorder recorder,
            ObjectFactory<LegProgress> legs) {
        this(parcels, proofs, recorder, legs, Clock.systemUTC());
    }

    ParcelStatusService(ParcelRepository parcels, DeliveryProofRepository proofs, ParcelEventRecorder recorder,
            ObjectFactory<LegProgress> legs, Clock clock) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.recorder = recorder;
        this.legs = legs;
        this.clock = clock;
    }

    /**
     * Applies a status change by the parcel's assigned courier. In v1 nobody else changes status
     * after creation (ADR 0006 §2).
     *
     * @throws ParcelNotFoundException if the parcel does not exist or the courier is not assigned to it
     * @throws com.swiftdrop.delivery.api.ParcelConflictException if the change breaks a lifecycle rule
     * @throws IllegalArgumentException if the request itself is invalid (reason for a non-failure,
     *         note too long)
     */
    @Transactional
    public ParcelStatusChanged changeStatus(UUID parcelId, StatusChange change, Actor courier) {
        return changeStatus(parcelId, change, courier, null);
    }

    /**
     * Like {@link #changeStatus(UUID, StatusChange, Actor)}, for a courier who confirms arrival at {@code hubId}. For
     * a parcel with legs (ADR 0007) {@code HUB_ARRIVED} needs that hub, which must be the destination of the leg in
     * progress; the leg then ends. Delivery or failure ends the final leg. A parcel without legs ignores the hub.
     */
    @Transactional
    public ParcelStatusChanged changeStatus(UUID parcelId, StatusChange change, Actor courier, UUID arrivedAtHubId) {
        Objects.requireNonNull(change, "change");
        if (courier.role() != ActorRole.COURIER) {
            throw new IllegalArgumentException("Only couriers change a parcel's status");
        }
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        ParcelStatus previous = parcel.getStatus();
        // Checked in this transaction, under the parcel lock (ADR 0006 §5)
        boolean proofCaptured = change.toStatus() == ParcelStatus.DELIVERED && proofs.existsByParcelId(parcelId);
        Instant now = clock.instant();

        parcel.changeStatus(change.toStatus(), courier.userId(), change.failureReason(), proofCaptured, now);
        // After the parcel accepted the move, so a stranger still gets the plain 404 of ADR 0006 section 7
        legs.getObject().requireMoveAllowed(parcelId, courier.userId(), previous, change.toStatus());
        if (change.toStatus() == ParcelStatus.HUB_ARRIVED) {
            legs.getObject().arrivedAtHub(parcelId, arrivedAtHubId, now);
        } else if (change.toStatus() == ParcelStatus.DELIVERED || change.toStatus() == ParcelStatus.FAILED) {
            legs.getObject().finished(parcelId, now);
        }
        return recorder.record(parcel, previous, courier, change.note(), change.failureReason(), now);
    }
}
