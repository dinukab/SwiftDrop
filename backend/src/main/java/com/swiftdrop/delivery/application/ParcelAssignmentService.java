package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * {@link ParcelAssignment} for {@code dispatch} (ADM-1). {@code dispatch} has already checked that
 * the courier is eligible; this records the assignment under the parcel lock so it cannot race a
 * pickup.
 */
@Service
class ParcelAssignmentService implements ParcelAssignment {

    private static final Logger log = LoggerFactory.getLogger(ParcelAssignmentService.class);

    private final ParcelRepository parcels;
    private final ObjectFactory<LegProgress> legs;
    private final Clock clock;

    @Autowired
    ParcelAssignmentService(ParcelRepository parcels, ObjectFactory<LegProgress> legs) {
        this(parcels, legs, Clock.systemUTC());
    }

    ParcelAssignmentService(ParcelRepository parcels, ObjectFactory<LegProgress> legs, Clock clock) {
        this.parcels = parcels;
        this.legs = legs;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void assignCourier(UUID parcelId, UUID courierUserId, UUID dispatcherUserId) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(courierUserId, "courierUserId");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId");
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        var now = clock.instant();
        parcel.assignCourier(courierUserId, now);
        // A parcel with legs: the pickup courier also holds the first leg (ADR 0007)
        legs.getObject().syncPickupCourier(parcelId, courierUserId, now);
        // IDs only: no personal data in logs
        log.info("Parcel {} assigned to courier {} by dispatcher {}", parcelId, courierUserId, dispatcherUserId);
    }
}
