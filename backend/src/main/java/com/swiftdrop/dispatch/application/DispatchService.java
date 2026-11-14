package com.swiftdrop.dispatch.application;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.shared.web.PageLimit;

/**
 * Dispatcher use cases (SD-306): find parcels waiting for a courier and assign them. Lifecycle
 * rules stay in {@code delivery}; dispatch only decides who is eligible.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final ParcelQueries parcelQueries;
    private final ParcelAssignment parcelAssignment;
    private final CourierEligibility eligibility;

    public DispatchService(ParcelQueries parcelQueries, ParcelAssignment parcelAssignment,
            CourierEligibility eligibility) {
        this.parcelQueries = Objects.requireNonNull(parcelQueries, "parcelQueries");
        this.parcelAssignment = Objects.requireNonNull(parcelAssignment, "parcelAssignment");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    /** CREATED parcels without a courier, oldest first; page and size are clamped. */
    public PageResult<ParcelSummary> unassigned(int page, int size) {
        return parcelQueries.findUnassignedParcels(
                new PageRequest(PageLimit.clampPage(page), PageLimit.clampSize(size)));
    }

    /**
     * Assigns the parcel to an eligible courier. The parcel is never touched for an ineligible one.
     *
     * @throws CourierNotEligibleException if the courier cannot receive parcels
     * @throws com.swiftdrop.delivery.api.ParcelNotFoundException if the parcel does not exist
     * @throws com.swiftdrop.delivery.api.ParcelConflictException if the parcel is past CREATED
     */
    public void assign(UUID parcelId, UUID courierUserId, UUID dispatcherUserId) {
        Objects.requireNonNull(parcelId, "parcelId must not be null");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId must not be null");
        eligibility.requireEligible(courierUserId);
        parcelAssignment.assignCourier(parcelId, courierUserId, dispatcherUserId);
        log.info("Parcel {} assigned to courier {} by dispatcher {}", parcelId, courierUserId, dispatcherUserId);
    }
}
