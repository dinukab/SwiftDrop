package com.swiftdrop.dispatch.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

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
     * The eligibility rule without exceptions, for lists such as the workload view: the account is
     * enabled, holds the {@code COURIER} role and has an active profile. Keep in step with
     * {@link #requireEligible}, which reports which rule failed.
     */
    public static boolean isEligible(UserSummary user, CourierProfile profile) {
        return user != null && user.enabled() && user.hasRole(COURIER_ROLE)
                && profile != null && profile.isActive();
    }

    /**
     * A courier that is based at a hub (home or current) may only be given work by that hub's dispatcher.
     * A courier with no hub recorded is not restricted, so couriers set up before hubs existed keep working.
     * Call after {@link #requireEligible}.
     *
     * @throws CourierNotEligibleException if the courier is based at other hubs only
     */
    public void requireAtHub(UUID courierUserId, UUID hubId) {
        Objects.requireNonNull(courierUserId, "courierUserId");
        Objects.requireNonNull(hubId, "hubId");
        profiles.findByUserId(courierUserId).ifPresent(profile -> {
            boolean located = profile.getHomeHubId() != null || profile.getCurrentHubId() != null;
            if (located && !hubId.equals(profile.getHomeHubId()) && !hubId.equals(profile.getCurrentHubId())) {
                throw new CourierNotEligibleException("Courier is not based at this hub");
            }
        });
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
