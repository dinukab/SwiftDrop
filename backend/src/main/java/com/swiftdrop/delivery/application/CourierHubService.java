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
 * Courier hub arrival confirmation for hub-based delivery. The status change goes through
 * {@link ParcelStatusService}, so it takes the parcel lock and writes the history row and the outbox
 * event like every other status change.
 */
@Service
@Lazy
public class CourierHubService {

    private final ParcelStatusService statuses;
    private final ParcelRepository parcels;

    @Autowired
    public CourierHubService(ParcelStatusService statuses, ParcelRepository parcels) {
        this.statuses = Objects.requireNonNull(statuses, "statuses");
        this.parcels = Objects.requireNonNull(parcels, "parcels");
    }

    /**
     * Confirms that a courier has arrived at a hub with a parcel and moves it to {@code HUB_ARRIVED}.
     * {@code hubId} is not validated or stored yet: the parcel does not record which hub it is at.
     *
     * @return the updated parcel
     * @throws ParcelNotFoundException if the parcel is unknown or not assigned to the courier
     * @throws com.swiftdrop.delivery.api.ParcelConflictException if the parcel cannot move to
     *         {@code HUB_ARRIVED} from its current status
     */
    @Transactional
    public Parcel confirmHubArrival(UUID parcelId, UUID hubId, UUID courierId) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(hubId, "hubId must not be null");
        Objects.requireNonNull(courierId, "courierId must not be null");
        statuses.changeStatus(parcelId, new StatusChange(ParcelStatus.HUB_ARRIVED, null, null),
                Actor.courier(courierId));
        return parcels.findById(parcelId).orElseThrow(ParcelNotFoundException::new);
    }
}
