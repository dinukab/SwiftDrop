package com.swiftdrop.dispatch.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A provincial hub: reference data seeded by migration, read-only in the application. {@code code}
 * is the province code (for example {@code LK-UP}); Colombo ({@code LK-WP}) is the Main Hub.
 */
@Entity
@Table(name = "hubs")
public class Hub {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "main", nullable = false)
    private boolean main;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Hub() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isMain() {
        return main;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Hub[code=" + code + ", main=" + main + "]";
    }
}
