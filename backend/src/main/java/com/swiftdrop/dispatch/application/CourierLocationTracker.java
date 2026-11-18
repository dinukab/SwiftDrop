package com.swiftdrop.dispatch.application;

import java.util.Comparator;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import com.swiftdrop.delivery.api.LegView;
import com.swiftdrop.delivery.api.ParcelLegs;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;

/**
 * Keeps a courier's <em>current hub</em> up to date (Phase 7): when a courier confirms arrival at a hub,
 * the leg they just finished ends there, so that hub becomes their current hub. It reads the legs through
 * {@code delivery.api} and runs after the status change is committed, so a failure here can never undo
 * or block the courier's own action.
 */
@Component
public class CourierLocationTracker {

    private static final Logger log = LoggerFactory.getLogger(CourierLocationTracker.class);

    private final ObjectFactory<ParcelLegs> legs;
    private final ObjectFactory<CourierProfileService> profiles;

    public CourierLocationTracker(ObjectFactory<ParcelLegs> legs, ObjectFactory<CourierProfileService> profiles) {
        this.legs = Objects.requireNonNull(legs, "legs");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStatusChanged(ParcelStatusChanged event) {
        if (event.data().newStatus() != ParcelStatus.HUB_ARRIVED) {
            return;
        }
        try {
            // The leg that just ended is the completed leg with the highest sequence
            legs.getObject().findLegs(event.data().parcelId()).stream()
                    .filter(leg -> leg.completedAt() != null && leg.assignedCourierId() != null)
                    .max(Comparator.comparingInt(LegView::sequence))
                    .ifPresent(this::moveCourier);
        } catch (RuntimeException e) {
            // Location is informational; never fail the courier's arrival because of it
            log.warn("Could not update the courier hub for parcel {}: {}", event.data().parcelId(), e.toString());
        }
    }

    private void moveCourier(LegView leg) {
        CourierProfileService service = profiles.getObject();
        service.findByUserId(leg.assignedCourierId())
                .ifPresent(profile -> service.moveToHub(profile.getId(), leg.destinationHubId()));
    }
}
