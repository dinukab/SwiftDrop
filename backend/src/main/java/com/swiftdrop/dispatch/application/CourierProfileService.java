package com.swiftdrop.dispatch.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final Clock clock;

    @Autowired
    public CourierProfileService(CourierProfileRepository profiles, UserDirectory userDirectory) {
        this(profiles, userDirectory, Clock.systemUTC());
    }

    CourierProfileService(CourierProfileRepository profiles, UserDirectory userDirectory, Clock clock) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory");
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

    public Optional<CourierProfile> findByUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return profiles.findByUserId(userId);
    }

    /**
     * Lists profiles, oldest first with ID as tie-breaker; {@code active} null means no filter.
     * Page and size are clamped with {@link PageLimit}.
     */
    public Page<CourierProfile> listProfiles(Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(
                PageLimit.clampPage(page),
                PageLimit.clampSize(size),
                Sort.by("createdAt", "id"));
        return active == null ? profiles.findAll(pageable) : profiles.findByActive(active, pageable);
    }

    /** @throws CourierProfileNotFoundException if no profile has {@code profileId} */
    public CourierProfile getProfile(UUID profileId) {
        Objects.requireNonNull(profileId, "profileId must not be null");
        return profiles.findById(profileId).orElseThrow(CourierProfileNotFoundException::new);
    }

    /**
     * Replaces phone and vehicle type.
     *
     * @throws CourierProfileNotFoundException if no profile has {@code profileId}
     * @throws IllegalArgumentException if the phone is invalid (nothing is saved)
     */
    @Transactional
    public CourierProfile updateDetails(UUID profileId, String phone, VehicleType vehicleType) {
        CourierProfile profile = getProfile(profileId);
        profile.updateDetails(phone, vehicleType, clock.instant());
        log.info("Courier profile {} details updated", profileId);
        return profile;
    }

    /**
     * Activates or deactivates a profile; inactive couriers receive no new assignments.
     *
     * @throws CourierProfileNotFoundException if no profile has {@code profileId}
     */
    @Transactional
    public CourierProfile setActive(UUID profileId, boolean active) {
        CourierProfile profile = getProfile(profileId);
        profile.setActive(active, clock.instant());
        log.info("Courier profile {} active={}", profileId, active);
        return profile;
    }
}

