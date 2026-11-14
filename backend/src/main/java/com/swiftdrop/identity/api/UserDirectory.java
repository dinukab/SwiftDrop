package com.swiftdrop.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Public facade of the identity module for other modules (ADR 0003). Provides read-only user
 * summaries and merchant user creation without leaking internal domain entities.
 */
public interface UserDirectory {

    /**
     * Looks up an existing user by their unique identifier.
     *
     * @param id the user ID
     * @return the user summary, or empty if no user exists with this ID
     */
    Optional<UserSummary> findUser(UUID id);

    /**
     * Whether the user may currently exercise courier authority. Publicly registered couriers
     * require an approved {@code courier_approvals} row; admin-created couriers (no row) and
     * non-courier users return {@code true} so callers only strip the COURIER authority.
     *
     * @param id the user ID
     * @return {@code false} only when a courier approval row exists and is not APPROVED
     */
    boolean isCourierApproved(UUID id);

    /**
     * Creates a new user with the {@code MERCHANT} role.
     *
     * @param fullName the user's full name
     * @param email the user's email address
     * @param rawPassword the user's plaintext password
     * @return the newly created user's ID
     */
    UUID createMerchantUser(String fullName, String email, String rawPassword);
}
