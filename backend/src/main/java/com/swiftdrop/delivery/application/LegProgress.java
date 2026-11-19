package com.swiftdrop.delivery.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.domain.ShipmentLeg;
import com.swiftdrop.delivery.domain.ShipmentLegRepository;

/**
 * Keeps a parcel's legs in step with its status (ADR 0007). Every method runs inside the caller's
 * transaction, under the parcel lock, and does nothing for a parcel that has no legs, which keeps the
 * single-courier behaviour of ADR 0006.
 */
@Component
@Lazy
class LegProgress {

    private final ShipmentLegRepository legs;

    LegProgress(ShipmentLegRepository legs) {
        this.legs = Objects.requireNonNull(legs, "legs");
    }

    /** The courier of a new pickup assignment also holds the first leg. */
    void syncPickupCourier(UUID parcelId, UUID courierUserId, Instant now) {
        List<ShipmentLeg> all = legs.findByParcelIdOrderBySequenceAsc(parcelId);
        if (!all.isEmpty()) {
            all.get(0).assignCourier(courierUserId, now);
        }
    }

    /**
     * The courier confirmed arrival at {@code hubId}: that must be the destination hub of the leg in progress,
     * which then ends.
     *
     * @throws ParcelConflictException if the hub is missing or wrong, or no leg ends at a hub now
     */
    void arrivedAtHub(UUID parcelId, UUID hubId, Instant now) {
        List<ShipmentLeg> all = legs.findByParcelIdOrderBySequenceAsc(parcelId);
        if (all.isEmpty()) {
            return;
        }
        if (hubId == null) {
            throw new ParcelConflictException("Confirm the arrival with the hub");
        }
        ShipmentLeg current = firstOpen(all);
        if (current == null || current.getLegType() == LegType.FINAL_MILE) {
            throw new ParcelConflictException("No leg of the parcel ends at a hub now");
        }
        if (!hubId.equals(current.getDestinationHubId())) {
            throw new ParcelConflictException("This is not the destination hub of the current leg");
        }
        current.complete(now);
    }

    /** The parcel was delivered or failed: the leg in progress ends. */
    void finished(UUID parcelId, Instant now) {
        ShipmentLeg current = firstOpen(legs.findByParcelIdOrderBySequenceAsc(parcelId));
        if (current != null) {
            current.complete(now);
        }
    }

    private static ShipmentLeg firstOpen(List<ShipmentLeg> ordered) {
        return ordered.stream().filter(leg -> !leg.isCompleted()).findFirst().orElse(null);
    }
}
