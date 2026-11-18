package com.swiftdrop.dispatch.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for hubs; restricted to the {@code dispatch} module (ADR 0003). */
public interface HubRepository extends JpaRepository<Hub, UUID> {

    Optional<Hub> findByCode(String code);

    /** The Main Hub, if it is active. */
    Optional<Hub> findByMainTrueAndActiveTrue();

    /** Active hubs, Main Hub first, then by name. */
    List<Hub> findByActiveTrueOrderByMainDescNameAsc();
}
