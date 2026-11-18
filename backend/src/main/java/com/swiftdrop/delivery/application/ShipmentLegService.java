package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.LegPlan;
import com.swiftdrop.delivery.api.LegType;
import com.swiftdrop.delivery.api.LegView;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelLegs;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.WaitingLeg;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ShipmentLeg;
import com.swiftdrop.delivery.domain.ShipmentLegRepository;

/** {@link ParcelLegs} for {@code dispatch} (ADR 0007); changes run under the parcel lock. */
@Service
@Lazy
@Transactional(readOnly = true)
class ShipmentLegService implements ParcelLegs {

    private static final Logger log = LoggerFactory.getLogger(ShipmentLegService.class);
    private static final Set<ParcelStatus> AT_HUB = EnumSet.of(ParcelStatus.HUB_ARRIVED, ParcelStatus.HUB_SORTED);

    private final ParcelRepository parcels;
    private final ShipmentLegRepository legs;
    private final Clock clock;

    @Autowired
    ShipmentLegService(ParcelRepository parcels, ShipmentLegRepository legs) {
        this(parcels, legs, Clock.systemUTC());
    }

    ShipmentLegService(ParcelRepository parcels, ShipmentLegRepository legs, Clock clock) {
        this.parcels = Objects.requireNonNull(parcels, "parcels");
        this.legs = Objects.requireNonNull(legs, "legs");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public boolean planLegs(UUID parcelId, List<LegPlan> plan) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(plan, "plan");
        requireWellFormed(plan);
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        // Under the parcel lock: a concurrent assignment may have planned the legs while we waited
        if (legs.existsByParcelId(parcelId)) {
            return false;
        }
        if (parcel.getStatus() != ParcelStatus.CREATED) {
            throw new ParcelConflictException("Legs can only be planned before pickup");
        }
        var now = clock.instant();
        int sequence = 1;
        for (LegPlan leg : plan) {
            legs.save(new ShipmentLeg(parcelId, sequence++, leg.legType(), leg.sourceHubId(),
                    leg.destinationHubId(), now));
        }
        legs.flush();
        log.info("Parcel {} planned with {} legs", parcelId, plan.size());
        return true;
    }

    /**
     * Own read-only transaction, so no managed copy of the legs stays in the caller's persistence context: a
     * dispatcher assigns a leg right after reading it, and a copy from before the parcel lock could be stale.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<LegView> findLegs(UUID parcelId) {
        Objects.requireNonNull(parcelId, "parcelId");
        return legs.findByParcelIdOrderBySequenceAsc(parcelId).stream().map(ShipmentLegService::view).toList();
    }

    @Override
    public PageResult<WaitingLeg> findWaitingLegs(UUID sourceHubId, PageRequest page) {
        Objects.requireNonNull(sourceHubId, "sourceHubId");
        Objects.requireNonNull(page, "page");
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page.page(), page.size());
        Page<ShipmentLeg> found = legs.findWaiting(sourceHubId, AT_HUB, pageable);
        Map<UUID, Parcel> byId = new HashMap<>();
        for (Parcel parcel : parcels.findAllById(found.getContent().stream().map(ShipmentLeg::getParcelId).toList())) {
            byId.put(parcel.getId(), parcel);
        }
        List<WaitingLeg> items = found.getContent().stream()
                .map(leg -> {
                    Parcel parcel = byId.get(leg.getParcelId());
                    return new WaitingLeg(view(leg), parcel.getTrackingNumber(), parcel.getSize(),
                            parcel.getWeightGrams());
                })
                .toList();
        return PageResult.of(items, page, found.getTotalElements());
    }

    @Override
    @Transactional
    public LegView assignLegCourier(UUID parcelId, UUID legId, UUID courierUserId, UUID dispatcherUserId) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(legId, "legId");
        Objects.requireNonNull(courierUserId, "courierUserId");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId");
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        ShipmentLeg leg = legs.findByIdAndParcelId(legId, parcelId).orElseThrow(ParcelNotFoundException::new);
        if (leg.getSequence() == 1) {
            throw new ParcelConflictException("The pickup courier is assigned with the pickup assignment");
        }
        List<ShipmentLeg> all = legs.findByParcelIdOrderBySequenceAsc(parcelId);
        ShipmentLeg previous = all.get(leg.getSequence() - 2);
        if (!previous.isCompleted()) {
            throw new ParcelConflictException("The previous leg is not complete yet");
        }
        // The leg must not be complete (checked by the leg) and the parcel must wait at a hub (by the parcel)
        leg.assignCourier(courierUserId, clock.instant());
        parcel.handOverTo(courierUserId, clock.instant());
        legs.flush();
        // IDs only: no names in logs
        log.info("Parcel {} leg {} handed to courier {} by dispatcher {}", parcelId, leg.getSequence(),
                courierUserId, dispatcherUserId);
        return view(leg);
    }

    private static void requireWellFormed(List<LegPlan> plan) {
        if (plan.size() < 2 || plan.get(0).legType() != LegType.PICKUP
                || plan.get(plan.size() - 1).legType() != LegType.FINAL_MILE) {
            throw new ParcelConflictException("A route starts with a pickup and ends with a final leg");
        }
        for (LegPlan leg : plan.subList(1, plan.size() - 1)) {
            if (leg.legType() != LegType.INTER_HUB) {
                throw new ParcelConflictException("Only hub-to-hub legs fit between pickup and final leg");
            }
        }
    }

    private static LegView view(ShipmentLeg leg) {
        return new LegView(leg.getId(), leg.getParcelId(), leg.getSequence(), leg.getLegType(),
                leg.getSourceHubId(), leg.getDestinationHubId(), leg.getAssignedCourierId(), leg.getAssignedAt(),
                leg.getCompletedAt());
    }
}
