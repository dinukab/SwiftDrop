package com.swiftdrop.dispatch.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/**
 * The single rule for who may receive parcels (contract §5.4): the account is enabled, holds the
 * {@code COURIER} role and has an active courier profile.
 */
@Component
public class CourierEligibility {

    private static final String COURIER_ROLE = "COURIER";

    private final UserDirectory userDirectory;
    private final CourierProfileRepository profiles;

    public CourierEligibility(UserDirectory userDirectory, CourierProfileRepository profiles) {
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    /** @throws CourierNotEligibleException naming the first rule the user breaks */
    public void requireEligible(UUID courierUserId) {
        Objects.requireNonNull(courierUserId, "courierUserId must not be null");
        UserSummary user = userDirectory.findUser(courierUserId)
                .orElseThrow(() -> new CourierNotEligibleException("Courier not found"));
        if (!isEligible(user, profiles.findByUserId(courierUserId).orElse(null))) {
            throw new CourierNotEligibleException(reasonFor(user, courierUserId));
        }
    }

    /** True when the account and profile together satisfy the rule; used by the workload view. */
    public static boolean isEligible(UserSummary user, CourierProfile profile) {
        return user != null && user.enabled() && user.hasRole(COURIER_ROLE)
                && profile != null && profile.isActive();
    }

    private String reasonFor(UserSummary user, UUID courierUserId) {
        if (!user.enabled()) {
            return "Courier account is disabled";
        }
        if (!user.hasRole(COURIER_ROLE)) {
            return "User is not a courier";
        }
        CourierProfile profile = profiles.findByUserId(courierUserId).orElse(null);
        return profile == null ? "Courier has no profile" : "Courier profile is inactive";
    }
}
