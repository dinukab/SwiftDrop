package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.delivery.api.LegView;
import com.swiftdrop.delivery.api.ParcelLegs;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelLifecycle;
import com.swiftdrop.delivery.domain.ParcelRepository;

/**
 * A courier's deliveries for the day (COU-2, D-09): their unfinished parcels plus the ones they
 * finished today. "Today" starts at midnight in the business time zone ({@code Asia/Colombo} by
 * default), not in UTC.
 */
@Service
@Transactional(readOnly = true)
public class CourierParcelService {

    private static final Set<ParcelStatus> TERMINAL = ParcelStatus.finalStatuses();

    private final ParcelRepository parcels;
    private final DeliveryProofRepository proofs;
    private final ObjectFactory<ParcelLegs> legs;
    private final ZoneId businessZone;
    private final Clock clock;

    @Autowired
    public CourierParcelService(ParcelRepository parcels, DeliveryProofRepository proofs,
            ObjectFactory<ParcelLegs> legs,
            @Value("${swiftdrop.delivery.business-time-zone:Asia/Colombo}") String businessZone) {
        this(parcels, proofs, legs, ZoneId.of(businessZone), Clock.systemUTC());
    }

    CourierParcelService(ParcelRepository parcels, DeliveryProofRepository proofs, ObjectFactory<ParcelLegs> legs,
            ZoneId businessZone, Clock clock) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.legs = legs;
        this.businessZone = businessZone;
        this.clock = clock;
    }

    /**
     * One parcel on the courier's list.
     *
     * @param arrivalHubId the hub of the pickup address's zone, where the courier takes a picked-up parcel; null
     *                     when the parcel is not picked up or has no legs
     */
    public record CourierParcel(Parcel parcel, boolean proofCaptured, Set<ParcelStatus> nextStatuses,
            UUID arrivalHubId) {

        public CourierParcel {
            Objects.requireNonNull(parcel, "parcel");
            nextStatuses = Set.copyOf(nextStatuses);
        }

        /** Never includes personal data. */
        @Override
        public String toString() {
            return "CourierParcel[parcel=" + parcel + ", proofCaptured=" + proofCaptured + "]";
        }
    }

    public List<CourierParcel> today(UUID courierId) {
        Objects.requireNonNull(courierId, "courierId");
        List<Parcel> day = parcels.findCourierDay(courierId, TERMINAL, startOfToday());
        if (day.isEmpty()) {
            return List.of();
        }
        Set<UUID> withProof = proofs.findParcelIdsWithProof(day.stream().map(Parcel::getId).toList());
        return day.stream().map(parcel -> {
            List<LegView> parcelLegs = legs.getObject().findLegs(parcel.getId());
            return new CourierParcel(parcel, withProof.contains(parcel.getId()),
                    nextStatuses(parcel, courierId, parcelLegs), arrivalHub(parcel, parcelLegs));
        }).toList();
    }

    /**
     * What the courier may do next (ADR 0006 section 1B): for a parcel with legs only what the courier's open leg
     * allows, nothing when the courier holds no open leg any more.
     */
    private static Set<ParcelStatus> nextStatuses(Parcel parcel, UUID courierId, List<LegView> parcelLegs) {
        Set<ParcelStatus> next = ParcelLifecycle.nextStatuses(parcel.getStatus());
        if (parcelLegs.isEmpty()) {
            return next;
        }
        return parcelLegs.stream()
                .filter(leg -> leg.completedAt() == null && courierId.equals(leg.assignedCourierId()))
                .findFirst()
                .map(leg -> next.stream()
                        .filter(to -> ParcelLifecycle.canMove(leg.legType(), parcel.getStatus(), to))
                        .collect(java.util.stream.Collectors.toSet()))
                .orElse(Set.of());
    }

    /**
     * After pickup the courier brings the parcel to the pickup zone's hub, not the destination's. Moving it on to
     * the destination zone is not part of the courier's job.
     */
    private static UUID arrivalHub(Parcel parcel, List<LegView> parcelLegs) {
        if (parcel.getStatus() != ParcelStatus.PICKED_UP) {
            return null;
        }
        return parcelLegs.stream()
                .filter(leg -> leg.legType() == LegType.PICKUP)
                .map(LegView::destinationHubId)
                .findFirst()
                .orElse(null);
    }

    Instant startOfToday() {
        return LocalDate.now(clock.withZone(businessZone)).atStartOfDay(businessZone).toInstant();
    }
}
