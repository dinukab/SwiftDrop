package com.swiftdrop.identity.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierApprovalRepository extends JpaRepository<CourierApproval, UUID> {

    Page<CourierApproval> findByStatus(CourierApprovalStatus status, Pageable pageable);

    Optional<CourierApproval> findByUserId(UUID userId);
}
