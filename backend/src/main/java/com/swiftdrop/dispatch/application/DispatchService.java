package com.swiftdrop.dispatch.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.Hub;
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
    private final ObjectFactory<HubService> hubs;
    private final ObjectFactory<AssignmentHistoryService> history;

    public DispatchService(ParcelQueries parcelQueries, ParcelAssignment parcelAssignment,
            CourierEligibility eligibility, CourierProfileRepository profiles, UserDirectory users,
            ObjectFactory<HubService> hubs, ObjectFactory<AssignmentHistoryService> history) {
        this.parcelQueries = Objects.requireNonNull(parcelQueries, "parcelQueries");
        this.parcelAssignment = Objects.requireNonNull(parcelAssignment, "parcelAssignment");
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.users = Objects.requireNonNull(users, "users");
        this.hubs = Objects.requireNonNull(hubs, "hubs");
        this.history = Objects.requireNonNull(history, "history");
    }

    /**
     * Parcels in {@code CREATED} with no courier, oldest first, whose <em>pickup</em> is in the
     * dispatcher's own hub province. The hub is looked up here from the dispatcher's user ID, never
     * taken from the request.
     *
     * @throws DispatcherHubNotAssignedException if an admin has not bound the dispatcher to a hub
     */
    @Transactional(readOnly = true)
    public PageResult<ParcelSummary> unassigned(UUID dispatcherUserId, int page, int size) {
        Hub hub = requireHub(dispatcherUserId);
        return parcelQueries.findUnassignedParcels(new PageRequest(page, size), hub.getCode());
    }

    /**
     * Assigns a courier to a parcel that waits for pickup in the dispatcher's hub, after checking
     * eligibility. {@code delivery} then rejects the assignment unless the parcel is still
     * {@code CREATED}, under the parcel lock.
     *
     * <p>A parcel that does not exist and a parcel picked up in another hub both give
     * {@link ParcelNotFoundException}, so a dispatcher cannot tell another hub's parcels from missing
     * ones. The hub is checked before the courier, so nothing else leaks either.
     *
     * @throws DispatcherHubNotAssignedException if the dispatcher has no hub
     * @throws ParcelNotFoundException if the parcel does not exist or is not in the dispatcher's hub
     * @throws CourierNotEligibleException if the courier cannot take deliveries
     * @throws com.swiftdrop.delivery.api.ParcelConflictException if the parcel is past {@code CREATED}
     */
    @Transactional
    public void assign(UUID parcelId, UUID courierUserId, UUID dispatcherUserId) {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(courierUserId, "courierUserId");
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId");
        Hub hub = requireHub(dispatcherUserId);
        requireParcelInHub(parcelId, hub);
        eligibility.requireEligible(courierUserId);
        parcelAssignment.assignCourier(parcelId, courierUserId, dispatcherUserId);
        // Same transaction, after delivery holds the parcel lock: one active row per parcel
        history.getObject().record(parcelId, courierUserId, dispatcherUserId, hub.getId());
    }

    /**
     * Who was assigned to the parcel, by whom, when and for which hub, oldest first, for a dispatcher of
     * the hub the parcel is picked up in. Parcels assigned before history existed have no entries.
     *
     * @throws DispatcherHubNotAssignedException if the dispatcher has no hub
     * @throws ParcelNotFoundException if the parcel does not exist or is not in the dispatcher's hub
     */
    @Transactional(readOnly = true)
    public List<AssignmentHistoryEntry> assignmentHistory(UUID parcelId, UUID dispatcherUserId) {
        Objects.requireNonNull(parcelId, "parcelId");
        Hub hub = requireHub(dispatcherUserId);
        requireParcelInHub(parcelId, hub);
        Map<UUID, String> hubCodes = new HashMap<>();
        return history.getObject().history(parcelId).stream()
                .map(record -> new AssignmentHistoryEntry(
                        record.getId(),
                        record.getLegType().name(),
                        record.getCourierUserId(),
                        nameOf(record.getCourierUserId()),
                        record.getDispatcherUserId(),
                        nameOf(record.getDispatcherUserId()),
                        hubCodes.computeIfAbsent(record.getHubId(), id -> hubs.getObject().requireHub(id).getCode()),
                        record.getAssignedAt(),
                        record.getEndedAt(),
                        record.getEndReason() == null ? null : record.getEndReason().name()))
                .toList();
    }

    /** A parcel outside the dispatcher's hub looks exactly like a missing one. */
    private void requireParcelInHub(UUID parcelId, Hub hub) {
        ParcelSummary parcel = parcelQueries.findParcelById(parcelId).orElseThrow(ParcelNotFoundException::new);
        if (!hub.getCode().equals(parcel.pickupProvinceCode())) {
            throw new ParcelNotFoundException();
        }
    }

    /** Names are for the dispatcher's screen only, never for logs. */
    private String nameOf(UUID userId) {
        return users.findUser(userId).map(UserSummary::fullName).orElse("Unknown user");
    }

    private Hub requireHub(UUID dispatcherUserId) {
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId");
        return hubs.getObject().hubForDispatcher(dispatcherUserId).orElseThrow(DispatcherHubNotAssignedException::new);
    }

    /**
     * Every <em>eligible</em> courier with the number of parcels they hold, least busy first, so the
     * picker never offers a courier that {@link #assign} would refuse. Eligible means the same as for
     * assignment ({@link CourierEligibility#isEligible}): enabled account, {@code COURIER} role and an
     * active profile. A courier with no active parcels counts as 0.
     */
    @Transactional(readOnly = true)
    public List<CourierWorkload> workloads() {
        Map<UUID, Long> active = parcelQueries.countActiveParcelsByCourier();
        List<CourierProfile> activeProfiles = profiles.findByActive(true,
                org.springframework.data.domain.PageRequest.of(0, MAX_COURIERS)).getContent();

        return activeProfiles.stream()
                .map(profile -> {
                    UserSummary user = users.findUser(profile.getUserId()).orElse(null);
                    if (!CourierEligibility.isEligible(user, profile)) {
                        return null;
                    }
                    long count = active.getOrDefault(profile.getUserId(), 0L);
                    return new CourierWorkload(profile.getUserId(), user.fullName(),
                            profile.getVehicleType().name(), count);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(CourierWorkload::activeParcels)
                        .thenComparing(CourierWorkload::name))
                .toList();
    }

    /** One assignment in a parcel's history; {@code endedAt} and {@code endReason} are null while it is active. */
    public record AssignmentHistoryEntry(UUID id, String legType, UUID courierUserId, String courierName,
            UUID dispatcherUserId, String dispatcherName, String hubCode, Instant assignedAt, Instant endedAt,
            String endReason) {
    }

    /** One courier's load. Names are for the dispatcher's screen only, never for logs. */
    public record CourierWorkload(UUID courierUserId, String name, String vehicleType, long activeParcels) {
    }
}
