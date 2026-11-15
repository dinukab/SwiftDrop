package com.swiftdrop.dispatch.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.identity.api.UserDirectory;

/**
 * Courier eligibility for assignment (contract §5.4): the user is <b>enabled</b>, <b>has the
 * {@code COURIER} role</b>, and has an <b>active</b> courier profile. Checked by {@code dispatch}
 * before it asks {@code delivery} to record an assignment.
 */
@Service
public class CourierEligibility {

    static final String COURIER_ROLE = "COURIER";

    private final UserDirectory users;
    private final CourierProfileRepository profiles;

    public CourierEligibility(UserDirectory users, CourierProfileRepository profiles) {
        this.users = Objects.requireNonNull(users, "users");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    /**
     * @throws CourierNotEligibleException if the user is not an eligible courier
     */
    public void requireEligible(UUID courierUserId) {
        Objects.requireNonNull(courierUserId, "courierUserId");
        var user = users.findUser(courierUserId)
                .orElseThrow(() -> new CourierNotEligibleException("User is not a courier"));
        if (!user.enabled()) {
            throw new CourierNotEligibleException("Courier account is disabled");
        }
        if (!user.hasRole(COURIER_ROLE)) {
            throw new CourierNotEligibleException("User is not a courier");
        }
        if (profiles.findByUserId(courierUserId).filter(profile -> profile.isActive()).isEmpty()) {
            throw new CourierNotEligibleException("Courier profile is not active");
        }
    }
}
