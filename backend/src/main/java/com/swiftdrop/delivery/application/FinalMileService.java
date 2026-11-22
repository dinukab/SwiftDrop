package com.swiftdrop.delivery.application;

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

/**
 * Final-mile delivery workflow: pickup from the hub, then delivery to the customer. Every move goes
 * through {@link ParcelStatusService}, so it takes the parcel lock, writes the history row and the
 * outbox event, and {@code DELIVERED} still requires proof of delivery (ADR 0006 §5).
 */
@Service
@Lazy
public class FinalMileService {

    private final ParcelStatusService statuses;
    private final ParcelRepository parcels;

    @Autowired
    public FinalMileService(ParcelStatusService statuses, ParcelRepository parcels) {
        this.statuses = Objects.requireNonNull(statuses, "statuses");
        this.parcels = Objects.requireNonNull(parcels, "parcels");
    }

    /**
     * The courier takes the parcel from the hub for delivery: {@code OUT_FOR_DELIVERY}.
     *
     * @return the updated parcel
     * @throws ParcelNotFoundException if the parcel is unknown or not assigned to the courier
     * @throws com.swiftdrop.delivery.api.ParcelConflictException if the parcel cannot move to
     *         {@code OUT_FOR_DELIVERY} from its current status
     */
    @Transactional
    public Parcel pickupFromHub(UUID parcelId, UUID courierId) {
        return move(parcelId, courierId, ParcelStatus.OUT_FOR_DELIVERY);
    }

    /**
     * The courier delivered the parcel: {@code DELIVERED}. Proof must already be captured.
     *
     * @return the updated parcel
     * @throws ParcelNotFoundException if the parcel is unknown or not assigned to the courier
     * @throws com.swiftdrop.delivery.api.ParcelConflictException if the parcel is not
     *         {@code OUT_FOR_DELIVERY} or proof of delivery is missing
     */
    @Transactional
    public Parcel completeDelivery(UUID parcelId, UUID courierId) {
        return move(parcelId, courierId, ParcelStatus.DELIVERED);
    }

    private Parcel move(UUID parcelId, UUID courierId, ParcelStatus target) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(courierId, "courierId must not be null");
        statuses.changeStatus(parcelId, new StatusChange(target, null, null), Actor.courier(courierId));
        return parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
    }
}
