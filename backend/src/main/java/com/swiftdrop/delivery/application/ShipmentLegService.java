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
    private final ParcelEventRecorder recorder;
    private final Clock clock;

    @Autowired
    ShipmentLegService(ParcelRepository parcels, ShipmentLegRepository legs, ParcelEventRecorder recorder) {
        this(parcels, legs, recorder, Clock.systemUTC());
    }

    ShipmentLegService(ParcelRepository parcels, ShipmentLegRepository legs, ParcelEventRecorder recorder,
            Clock clock) {
        this.parcels = Objects.requireNonNull(parcels, "parcels");
        this.legs = Objects.requireNonNull(legs, "legs");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
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
        return toWaiting(legs.findWaiting(sourceHubId, AT_HUB, pageable(page)), page);
    }

    @Override
    public PageResult<WaitingLeg> findReadyTransfers(UUID sourceHubId, PageRequest page) {
        Objects.requireNonNull(sourceHubId, "sourceHubId");
        Objects.requireNonNull(page, "page");
        return toWaiting(legs.findReadyTransfers(sourceHubId, AT_HUB, pageable(page)), page);
    }

    @Override
    public PageResult<WaitingLeg> findIncomingLegs(UUID destinationHubId, PageRequest page) {
        Objects.requireNonNull(destinationHubId, "destinationHubId");
        Objects.requireNonNull(page, "page");
        return toWaiting(legs.findIncoming(destinationHubId, pageable(page)), page);
    }

    private static Pageable pageable(PageRequest page) {
        return org.springframework.data.domain.PageRequest.of(page.page(), page.size());
    }

    private PageResult<WaitingLeg> toWaiting(Page<ShipmentLeg> found, PageRequest page) {
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
        if (leg.getLegType() == LegType.INTER_HUB) {
            throw new ParcelConflictException("Hub-to-hub legs are transferred, not assigned to a courier");
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

    @Override
    @Transactional
    public LegView transferLeg(UUID parcelId, UUID legId, UUID dispatcherUserId) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(legId, "legId");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId");
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        ShipmentLeg leg = legs.findByIdAndParcelId(legId, parcelId).orElseThrow(ParcelNotFoundException::new);
        // The leg's own rules first (type, already sent, courier), so a second request reports a duplicate
        // transfer rather than "not at a hub"
        if (leg.getLegType() != LegType.INTER_HUB) {
            throw new ParcelConflictException("Only hub-to-hub legs are transferred");
        }
        if (leg.getTransferredAt() != null) {
            throw new ParcelConflictException("The parcel has already been transferred");
        }
        if (leg.isAssigned()) {
            throw new ParcelConflictException("The leg is already assigned to a courier");
        }
        List<ShipmentLeg> all = legs.findByParcelIdOrderBySequenceAsc(parcelId);
        if (!all.get(leg.getSequence() - 2).isCompleted()) {
            throw new ParcelConflictException("The previous leg is not complete yet");
        }
        var now = clock.instant();
        ParcelStatus previousStatus = parcel.getStatus();
        parcel.transferOut(now);
        leg.markTransferred(dispatcherUserId, now);
        recorder.record(parcel, previousStatus, Actor.dispatcher(dispatcherUserId), null, null, now);
        legs.flush();
        log.info("Parcel {} leg {} transferred by dispatcher {}", parcelId, leg.getSequence(), dispatcherUserId);
        return view(leg);
    }

    @Override
    @Transactional
    public LegView confirmLegReceipt(UUID parcelId, UUID legId, UUID dispatcherUserId) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(legId, "legId");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId");
        Parcel parcel = parcels.findByIdForUpdate(parcelId).orElseThrow(ParcelNotFoundException::new);
        ShipmentLeg leg = legs.findByIdAndParcelId(legId, parcelId).orElseThrow(ParcelNotFoundException::new);
        if (leg.getLegType() != LegType.INTER_HUB) {
            throw new ParcelConflictException("Only hub-to-hub legs are received");
        }
        var now = clock.instant();
        ParcelStatus previousStatus = parcel.getStatus();
        // The leg checks it was sent and not received yet, the parcel that it is in transit
        leg.markReceived(dispatcherUserId, now);
        parcel.receiveAtHub(now);
        recorder.record(parcel, previousStatus, Actor.dispatcher(dispatcherUserId), null, null, now);
        legs.flush();
        log.info("Parcel {} leg {} received by dispatcher {}", parcelId, leg.getSequence(), dispatcherUserId);
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
                leg.getCompletedAt(), leg.getTransferredAt(), leg.getTransferredBy(), leg.getReceivedAt(),
                leg.getReceivedBy());
    }
}
