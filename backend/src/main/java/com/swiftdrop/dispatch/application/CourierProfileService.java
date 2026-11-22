package com.swiftdrop.dispatch.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;
import com.swiftdrop.shared.web.PageLimit;

/**
 * Courier operational profile lifecycle for {@code dispatch} (SD-305). Links profiles 1:1 to
 * existing enabled Identity users holding the {@code COURIER} role without duplicating account
 * ownership.
 */
@Service
@Transactional(readOnly = true)
public class CourierProfileService {

    private static final Logger log = LoggerFactory.getLogger(CourierProfileService.class);
    private static final String COURIER_ROLE = "COURIER";

    private final CourierProfileRepository profiles;
    private final UserDirectory userDirectory;
    private final ObjectFactory<HubService> hubs;
    private final Clock clock;

    @Autowired
    public CourierProfileService(CourierProfileRepository profiles, UserDirectory userDirectory, ObjectFactory<HubService> hubs) {
        this(profiles, userDirectory, hubs, Clock.systemUTC());
    }

    CourierProfileService(CourierProfileRepository profiles, UserDirectory userDirectory, ObjectFactory<HubService> hubs, Clock clock) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory");
        this.hubs = Objects.requireNonNull(hubs, "hubs");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates an active courier profile for an existing enabled {@code COURIER} user.
     *
     * @throws CourierUserNotFoundException if no user exists for {@code userId}
     * @throws CourierUserNotEligibleException if the user is disabled or lacks the {@code COURIER} role
     * @throws DuplicateCourierProfileException if a profile already exists for {@code userId}
     */
    @Transactional
    public CourierProfile createProfile(UUID userId, String phone, VehicleType vehicleType) {
        Objects.requireNonNull(userId, "userId must not be null");
        String normalizedPhone = CourierProfile.normalizePhone(phone);
        Objects.requireNonNull(vehicleType, "vehicleType must not be null");

        UserSummary user = userDirectory.findUser(userId)
                .orElseThrow(CourierUserNotFoundException::new);
        if (!user.enabled()) {
            throw new CourierUserNotEligibleException("Courier user account is disabled");
        }
        if (!user.hasRole(COURIER_ROLE)) {
            throw new CourierUserNotEligibleException("User does not have the COURIER role");
        }
        if (profiles.existsByUserId(userId)) {
            throw new DuplicateCourierProfileException();
        }

        CourierProfile profile = new CourierProfile(userId, normalizedPhone, vehicleType, clock.instant());
        try {
            CourierProfile saved = profiles.saveAndFlush(profile);
            // IDs only: phone numbers and user details are personal data
            log.info("Courier profile {} created for user {}", saved.getId(), userId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Concurrent creation lost the race; uq_couriers_user_id is the final guard
            throw new DuplicateCourierProfileException();
        }
    }

    /**
     * Creates an active courier profile with home hub assignment.
     *
     * @param homeHubId optional hub where courier is based
     * @throws CourierUserNotFoundException if no user exists
     * @throws CourierUserNotEligibleException if user is disabled or lacks COURIER role
     * @throws DuplicateCourierProfileException if profile already exists
     * @throws HubNotFoundException if home hub not found
     */
    @Transactional
    public CourierProfile createProfile(UUID userId, String phone, VehicleType vehicleType, UUID homeHubId) {
        CourierProfile profile = createProfile(userId, phone, vehicleType);
        if (homeHubId != null) {
            return assignHomeHub(profile.getId(), homeHubId);
        }
        return profile;
    }

    public Optional<CourierProfile> findByUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return profiles.findByUserId(userId);
    }

    /** Pages courier profiles, newest first, optionally filtered by the active flag. */
    public Page<CourierProfile> listProfiles(int page, int size, Boolean active) {
        Pageable pageable = PageRequest.of(PageLimit.clampPage(page), PageLimit.clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return active == null ? profiles.findAll(pageable) : profiles.findByActive(active, pageable);
    }

    /**
     * Updates a profile's phone and vehicle type. A {@code null} argument keeps the current value.
     *
     * @throws CourierProfileNotFoundException if no profile exists for {@code profileId}
     */
    @Transactional
    public CourierProfile updateProfile(UUID profileId, String phone, VehicleType vehicleType) {
        CourierProfile profile = requireProfile(profileId);
        profile.updateDetails(phone != null ? phone : profile.getPhone(),
                vehicleType != null ? vehicleType : profile.getVehicleType(), clock.instant());
        return profiles.saveAndFlush(profile);
    }

    /** Makes the courier available for dispatch again. */
    @Transactional
    public CourierProfile activate(UUID profileId) {
        CourierProfile profile = requireProfile(profileId);
        profile.activate(clock.instant());
        return profiles.saveAndFlush(profile);
    }

    /** Takes the courier out of dispatch without deleting the account or its history. */
    @Transactional
    public CourierProfile deactivate(UUID profileId) {
        CourierProfile profile = requireProfile(profileId);
        profile.deactivate(clock.instant());
        return profiles.saveAndFlush(profile);
    }

    /**
     * Assigns a courier to their home hub (where they operate from).
     *
     * @throws CourierProfileNotFoundException if no profile exists
     * @throws HubNotFoundException if hub not found
     */
    @Transactional
    public CourierProfile assignHomeHub(UUID profileId, UUID hubId) {
        CourierProfile profile = requireProfile(profileId);
        hubs.getObject().requireHub(hubId); // Validate hub exists
        profile.assignHomeHub(hubId, clock.instant());
        log.info("Courier {} assigned home hub {}", profileId, hubId);
        return profiles.saveAndFlush(profile);
    }

    /**
     * Updates courier's current hub location (for multi-hub operations).
     *
     * @throws CourierProfileNotFoundException if no profile exists
     * @throws HubNotFoundException if hub not found
     */
    @Transactional
    public CourierProfile moveToHub(UUID profileId, UUID hubId) {
        CourierProfile profile = requireProfile(profileId);
        hubs.getObject().requireHub(hubId); // Validate hub exists
        profile.moveToHub(hubId, clock.instant());
        log.info("Courier {} moved to hub {}", profileId, hubId);
        return profiles.saveAndFlush(profile);
    }

    /** @throws CourierProfileNotFoundException if no profile exists for {@code profileId} */
    public CourierProfile getProfile(UUID profileId) {
        return requireProfile(profileId);
    }

    private CourierProfile requireProfile(UUID profileId) {
        Objects.requireNonNull(profileId, "profileId must not be null");
        return profiles.findById(profileId).orElseThrow(CourierProfileNotFoundException::new);
    }
}

