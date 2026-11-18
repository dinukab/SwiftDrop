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

/** Final-mile delivery workflow: pickup from hub → delivery to customer. */
@Service
@Lazy
@Transactional(readOnly = true)
public class FinalMileService {

    private final ParcelRepository parcels;
    private final Clock clock;

    @Autowired
    public FinalMileService(ParcelRepository parcels) {
        this(parcels, Clock.systemUTC());
    }

    FinalMileService(ParcelRepository parcels, Clock clock) {
        this.parcels = Objects.requireNonNull(parcels, "parcels");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Final-mile courier picks up parcel from destination hub. Transitions parcel from
     * HUB_SORTED to OUT_FOR_DELIVERY, indicating the courier has taken it for delivery
     * to the customer address.
     *
     * @param parcelId — the parcel being picked up for delivery
     * @param courierId — the final-mile courier making the delivery
     * @return the updated parcel
     * @throws ParcelNotFoundException if parcel not found or not assigned to courier
     * @throws ParcelConflictException if parcel is not in HUB_SORTED status
     */
    @Transactional
    public Parcel pickupFromHub(UUID parcelId, UUID courierId) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(courierId, "courierId must not be null");

        Parcel parcel = parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
        if (!courierId.equals(parcel.getAssignedCourierId())) {
            throw new ParcelNotFoundException();
        }

        // Transition from HUB_SORTED to OUT_FOR_DELIVERY
        parcel.changeStatus(ParcelStatus.OUT_FOR_DELIVERY, courierId, null, false, clock.instant());
        return parcels.saveAndFlush(parcel);
    }

    /**
     * Final-mile courier has delivered the parcel to customer address. Transitions parcel
     * to DELIVERED status (requires proof of delivery to be captured separately).
     *
     * @param parcelId — the delivered parcel
     * @param courierId — the courier who made the delivery
     * @return the updated parcel
     * @throws ParcelNotFoundException if parcel not found or not assigned to courier
     * @throws ParcelConflictException if parcel is not in OUT_FOR_DELIVERY status or proof missing
     */
    @Transactional
    public Parcel completeDelivery(UUID parcelId, UUID courierId) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(courierId, "courierId must not be null");

        Parcel parcel = parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
        if (!courierId.equals(parcel.getAssignedCourierId())) {
            throw new ParcelNotFoundException();
        }

        // Transition from OUT_FOR_DELIVERY to DELIVERED (requires proof)
        parcel.changeStatus(ParcelStatus.DELIVERED, courierId, null, true, clock.instant());
        return parcels.saveAndFlush(parcel);
    }
}
