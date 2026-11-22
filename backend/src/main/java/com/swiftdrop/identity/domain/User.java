package com.swiftdrop.identity.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * A user account of any role. Hibernate generates the UUID when the user is first persisted.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    /** {@code null} for users who sign in only with an external provider. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when an administrator deletes the account; such accounts are anonymised and hidden. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_name", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected User() {
        // for JPA
    }

    public User(String email, String passwordHash, String fullName, Set<Role> roles) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.enabled = true;
        this.createdAt = Instant.now();
        this.roles = EnumSet.copyOf(Objects.requireNonNull(roles, "roles"));
        if (this.roles.isEmpty()) {
            throw new IllegalArgumentException("A user needs at least one role");
        }
    }

    /** Trims and lower-cases an email address, matching the database check constraint. */
    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft-deletes the account. The email is replaced by a unique placeholder so the real address can be
     * registered again, the name and password are removed, and the account is disabled. The roles stay, so
     * the record still shows what the user was.
     */
    public void markDeleted(Instant now) {
        this.deletedAt = Objects.requireNonNull(now, "now");
        this.enabled = false;
        this.email = "deleted-" + id + "@deleted.invalid";
        this.fullName = "Deleted user";
        this.passwordHash = null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
