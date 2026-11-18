package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;
/** Courier hub arrival confirmation for hub-based delivery. */
@Service
@Lazy
@Transactional(readOnly = true)
public class CourierHubService {

    private final ParcelRepository parcels;
    private final Clock clock;

    @Autowired
    public CourierHubService(ParcelRepository parcels) {
        this(parcels, Clock.systemUTC());
    }

    CourierHubService(ParcelRepository parcels, Clock clock) {
        this.parcels = Objects.requireNonNull(parcels, "parcels");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Confirms that a courier has arrived at a hub with a parcel. Updates parcel status to
     * HUB_ARRIVED.
     *
     * @param parcelId — the parcel being delivered to the hub
     * @param hubId — the destination hub (for context/validation; courier location tracking is separate)
     * @param courierId — the courier making the delivery
     * @return the updated parcel
     * @throws ParcelNotFoundException if parcel not found or not assigned to courier
     * @throws ParcelConflictException if parcel is not in a status that allows hub arrival
     */
    @Transactional
    public Parcel confirmHubArrival(UUID parcelId, UUID hubId, UUID courierId) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(hubId, "hubId must not be null");
        Objects.requireNonNull(courierId, "courierId must not be null");

        // Load parcel and verify assigned courier
        Parcel parcel = parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
        if (!courierId.equals(parcel.getAssignedCourierId())) {
            throw new ParcelNotFoundException();
        }

        // Update parcel status to HUB_ARRIVED
        parcel.changeStatus(ParcelStatus.HUB_ARRIVED, courierId, null, false, clock.instant());
        return parcels.saveAndFlush(parcel);
    }
}
