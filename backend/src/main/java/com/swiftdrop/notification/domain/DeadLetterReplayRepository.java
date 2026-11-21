package com.swiftdrop.notification.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for the dead-letter replay audit trail. */
public interface DeadLetterReplayRepository extends JpaRepository<DeadLetterReplay, UUID> {

    /** The audit list, newest first. */
    Page<DeadLetterReplay> findAllByOrderByReplayedAtDescIdDesc(Pageable pageable);
}
