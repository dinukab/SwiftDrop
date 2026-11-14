package com.swiftdrop.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import com.swiftdrop.dispatch.application.CourierProfileService;
import com.swiftdrop.dispatch.application.CourierUserNotEligibleException;
import com.swiftdrop.dispatch.application.CourierUserNotFoundException;
import com.swiftdrop.dispatch.application.DuplicateCourierProfileException;

/**
 * Dispatch courier profile persistence and service validation against real PostgreSQL 18. Each
 * test rolls back.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
@Transactional
class DispatchPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private CourierProfileRepository profiles;
    @Autowired
    private CourierProfileService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @Test
    void courierProfileRoundTripsAndUpdatesVersionAndTimestamps() {
        UUID userId = insertUser("courier-roundtrip", "COURIER", true);

        CourierProfile saved = profiles.saveAndFlush(
                new CourierProfile(userId, "+94771234567", VehicleType.BIKE, NOW));
        entityManager.clear();

        CourierProfile loaded = profiles.findByUserId(userId).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getUserId()).isEqualTo(userId);
        assertThat(loaded.getPhone()).isEqualTo("+94771234567");
        assertThat(loaded.getVehicleType()).isEqualTo(VehicleType.BIKE);
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(loaded.getUpdatedAt()).isEqualTo(NOW);
        assertThat(loaded.getVersion()).isZero();

        Instant later = NOW.plusSeconds(120);
        loaded.updateDetails("+94711234567", VehicleType.VAN, later);
        loaded.deactivate(later);
        profiles.saveAndFlush(loaded);
        entityManager.clear();

        CourierProfile updated = profiles.findById(saved.getId()).orElseThrow();
        assertThat(updated.getPhone()).isEqualTo("+94711234567");
        assertThat(updated.getVehicleType()).isEqualTo(VehicleType.VAN);
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getCreatedAt()).isEqualTo(NOW);
        assertThat(updated.getUpdatedAt()).isEqualTo(later);
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(profiles.findByActive(false, PageRequest.of(0, 10)).getContent())
                .extracting(CourierProfile::getId)
                .containsExactly(saved.getId());
    }

    @Test
    void databaseEnforcesUniqueUserForeignKeyAndVehicleTypeConstraints() {
        UUID userId = insertUser("courier-constraints", "COURIER", true);
        profiles.saveAndFlush(new CourierProfile(userId, "+94771234567", VehicleType.CAR, NOW));

        // Duplicate user_id violates uq_couriers_user_id
        assertThatThrownBy(() -> profiles.saveAndFlush(
                new CourierProfile(userId, "+94779999999", VehicleType.BIKE, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Non-existent user_id violates fk_couriers_user
        assertThatThrownBy(() -> profiles.saveAndFlush(
                new CourierProfile(UUID.randomUUID(), "+94771234567", VehicleType.VAN, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Invalid vehicle_type violates ck_couriers_vehicle_type
        UUID secondUserId = insertUser("courier-bad-vehicle", "COURIER", true);
        assertViolation("INSERT INTO couriers (id, user_id, phone, vehicle_type, active, created_at, updated_at, version) "
                + "VALUES (gen_random_uuid(), ?, '+94771234567', 'TRUCK', TRUE, now(), now(), 0)", secondUserId);
    }

    @Test
    void serviceValidatesUserRoleEnabledAndUniquenessAgainstDatabase() {
        UUID courierUserId = insertUser("valid-courier", "COURIER", true);
        UUID customerUserId = insertUser("customer-user", "CUSTOMER", true);
        UUID disabledCourierId = insertUser("disabled-courier", "COURIER", false);

        CourierProfile created = service.createProfile(courierUserId, "+94771234567", VehicleType.BIKE);
        assertThat(created.getId()).isNotNull();
        assertThat(profiles.existsByUserId(courierUserId)).isTrue();

        assertThatThrownBy(() -> service.createProfile(courierUserId, "+94778888888", VehicleType.CAR))
                .isInstanceOf(DuplicateCourierProfileException.class);

        assertThatThrownBy(() -> service.createProfile(UUID.randomUUID(), "+94771234567", VehicleType.BIKE))
                .isInstanceOf(CourierUserNotFoundException.class);

        assertThatThrownBy(() -> service.createProfile(customerUserId, "+94771234567", VehicleType.BIKE))
                .isInstanceOf(CourierUserNotEligibleException.class);

        assertThatThrownBy(() -> service.createProfile(disabledCourierId, "+94771234567", VehicleType.BIKE))
                .isInstanceOf(CourierUserNotEligibleException.class);
    }

    private void assertViolation(String sql, Object... args) {
        jdbc.execute("SAVEPOINT expected_violation");
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT expected_violation");
    }

    private UUID insertUser(String prefix, String roleName, boolean enabled) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?)",
                id, prefix + "-" + id + "@example.com", "Test " + prefix, enabled, Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, roleName);
        return id;
    }
}

