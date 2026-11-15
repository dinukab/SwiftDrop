package com.swiftdrop.dispatch.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/**
 * The dispatcher's work (ADM-1, ADM-2): the queue of unassigned parcels, courier assignment, and the
 * workload per courier. {@code dispatch} decides who may take a parcel; {@code delivery} decides
 * whether the parcel can still be assigned and records it (ADR 0003, ADR 0006 §3).
 */
@Service
public class DispatchService {

    /** Upper bound on couriers listed for the workload view; the fleet is small. */
    static final int MAX_COURIERS = 200;

    private final ParcelQueries parcelQueries;
    private final ParcelAssignment parcelAssignment;
    private final CourierEligibility eligibility;
    private final CourierProfileRepository profiles;
    private final UserDirectory users;

    public DispatchService(ParcelQueries parcelQueries, ParcelAssignment parcelAssignment,
            CourierEligibility eligibility, CourierProfileRepository profiles, UserDirectory users) {
        this.parcelQueries = Objects.requireNonNull(parcelQueries, "parcelQueries");
        this.parcelAssignment = Objects.requireNonNull(parcelAssignment, "parcelAssignment");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.users = Objects.requireNonNull(users, "users");
    }

    /** Parcels in {@code CREATED} with no courier, oldest first. */
    @Transactional(readOnly = true)
    public PageResult<ParcelSummary> unassigned(int page, int size) {
        return parcelQueries.findUnassignedParcels(new PageRequest(page, size));
    }

    /**
     * Assigns a courier after checking eligibility. {@code delivery} then rejects the assignment unless
     * the parcel is still {@code CREATED}, under the parcel lock.
     *
     * @throws CourierNotEligibleException if the courier cannot take deliveries
     * @throws com.swiftdrop.delivery.api.ParcelNotFoundException if the parcel does not exist
     * @throws com.swiftdrop.delivery.api.ParcelConflictException if the parcel is past {@code CREATED}
     */
    @Transactional
    public void assign(UUID parcelId, UUID courierUserId, UUID dispatcherUserId) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(courierUserId, "courierUserId");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId");
        eligibility.requireEligible(courierUserId);
        parcelAssignment.assignCourier(parcelId, courierUserId, dispatcherUserId);
    }

    /**
     * Every active courier with the number of parcels they hold, least busy first. A courier with no
     * active parcels counts as 0.
     */
    @Transactional(readOnly = true)
    public List<CourierWorkload> workloads() {
        Map<UUID, Long> active = parcelQueries.countActiveParcelsByCourier();
        List<CourierProfile> activeProfiles = profiles.findByActive(true,
                org.springframework.data.domain.PageRequest.of(0, MAX_COURIERS)).getContent();

        return activeProfiles.stream()
                .map(profile -> {
                    UserSummary user = users.findUser(profile.getUserId()).orElse(null);
                    String name = user != null ? user.fullName() : "Unknown courier";
                    long count = active.getOrDefault(profile.getUserId(), 0L);
                    return new CourierWorkload(profile.getUserId(), name, profile.getVehicleType().name(), count);
                })
                .sorted(Comparator.comparingLong(CourierWorkload::activeParcels)
                        .thenComparing(CourierWorkload::name))
                .toList();
    }

    /** One courier's load. Names are for the dispatcher's screen only, never for logs. */
    public record CourierWorkload(UUID courierUserId, String name, String vehicleType, long activeParcels) {
    }
}
