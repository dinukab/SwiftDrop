package com.swiftdrop.dispatch.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for courier profiles; restricted to the {@code dispatch} module (ADR 0003).
 */
public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {

    boolean existsByUserId(UUID userId);

    Optional<CourierProfile> findByUserId(UUID userId);

    Page<CourierProfile> findByActive(boolean active, Pageable pageable);
}

