package com.swiftdrop.dispatch.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for dispatcher-to-hub bindings; the ID is the dispatcher's user ID. */
public interface DispatcherHubRepository extends JpaRepository<DispatcherHub, UUID> {
}
