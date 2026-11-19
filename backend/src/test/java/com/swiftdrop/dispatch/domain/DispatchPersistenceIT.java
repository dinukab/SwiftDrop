package com.swiftdrop.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private HubRepository hubRepository;
    @Autowired
    private DispatcherHubRepository dispatcherHubs;
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
        assertEntityViolation(() -> profiles.saveAndFlush(
                new CourierProfile(userId, "+94779999999", VehicleType.BIKE, NOW)));

        // Non-existent user_id violates fk_couriers_user
        assertEntityViolation(() -> profiles.saveAndFlush(
                new CourierProfile(UUID.randomUUID(), "+94771234567", VehicleType.VAN, NOW)));

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

    /**
     * A failed statement aborts the whole PostgreSQL transaction, so each expected violation runs behind a
     * savepoint, and the failed entity is dropped from the persistence context.
     */
    private void assertEntityViolation(Runnable action) {
        jdbc.execute("SAVEPOINT expected_entity_violation");
        assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT expected_entity_violation");
        entityManager.clear();
    }

    private void assertViolation(String sql, Object... args) {
        jdbc.execute("SAVEPOINT expected_violation");
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT expected_violation");
    }


    @Test
    void migrationSeedsNineProvincialHubsWithColomboAsTheOnlyMainHub() {
        List<Hub> all = hubRepository.findAll();

        assertThat(all).extracting(Hub::getCode).containsExactlyInAnyOrder(
                "LK-WP", "LK-CP", "LK-SP", "LK-NP", "LK-EP", "LK-NWP", "LK-NCP", "LK-UP", "LK-SGP");
        assertThat(all).allMatch(Hub::isActive);
        assertThat(all).filteredOn(Hub::isMain).extracting(Hub::getCode).containsExactly("LK-WP");
        assertThat(hubRepository.findByActiveTrueOrderByMainDescNameAsc()).first()
                .extracting(Hub::getCode).isEqualTo("LK-WP");
    }

    @Test
    void databaseRejectsASecondMainHub() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO hubs (id, code, name, main, active, created_at) VALUES (?, 'LK-XX', 'Test', TRUE, TRUE, now())",
                UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dispatcherBindingRoundTripsAndIsUniquePerUser() {
        UUID userId = insertUser("dispatcher-hub", "DISPATCHER", true);
        Hub uva = hubRepository.findByCode("LK-UP").orElseThrow();
        dispatcherHubs.saveAndFlush(new DispatcherHub(userId, uva.getId(), NOW));
        entityManager.clear();

        DispatcherHub loaded = dispatcherHubs.findById(userId).orElseThrow();
        assertThat(loaded.getHubId()).isEqualTo(uva.getId());

        // A dispatcher with an unknown hub violates fk_dispatcher_hubs_hub
        UUID other = insertUser("dispatcher-bad-hub", "DISPATCHER", true);
        assertThatThrownBy(() -> dispatcherHubs.saveAndFlush(new DispatcherHub(other, UUID.randomUUID(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static final String INSERT_ASSIGNMENT = "INSERT INTO parcel_assignments (id, parcel_id, leg_type, "
            + "leg_sequence, courier_user_id, dispatcher_user_id, hub_id, assigned_at, ended_at, end_reason) "
            + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, now(), %s)";

    @Test
    void databaseAllowsOnlyOneActiveAssignmentPerParcelAndLeg() {
        UUID parcel = UUID.randomUUID();
        UUID courierA = insertUser("assign-courier-a", "COURIER", true);
        UUID courierB = insertUser("assign-courier-b", "COURIER", true);
        UUID dispatcher = insertUser("assign-dispatcher", "DISPATCHER", true);
        UUID hub = hubRepository.findByCode("LK-UP").orElseThrow().getId();
        String active = String.format(INSERT_ASSIGNMENT, "NULL, NULL");

        jdbc.update(active, parcel, "PICKUP", 1, courierA, dispatcher, hub);

        // A second active row for the same parcel and leg is rejected
        assertViolation(active, parcel, "PICKUP", 1, courierB, dispatcher, hub);
        // Another leg of the same parcel, and the same leg of another parcel, are fine
        jdbc.update(active, parcel, "FINAL_MILE", 4, courierB, dispatcher, hub);
        // Two hub-to-hub legs of one parcel (Uva to Southern through Colombo) are two legs, so both may be active
        jdbc.update(active, parcel, "INTER_HUB", 2, courierB, dispatcher, hub);
        jdbc.update(active, parcel, "INTER_HUB", 3, courierB, dispatcher, hub);
        jdbc.update(active, UUID.randomUUID(), "PICKUP", 1, courierB, dispatcher, hub);
        // An ended row does not count as active
        jdbc.update(String.format(INSERT_ASSIGNMENT, "now(), 'REASSIGNED'"), parcel, "PICKUP", 1, courierB,
                dispatcher, hub);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ? AND ended_at IS NULL",
                Integer.class, parcel)).isEqualTo(4);
    }

    @Test
    void databaseChecksTheAssignmentRows() {
        UUID courier = insertUser("check-courier", "COURIER", true);
        UUID dispatcher = insertUser("check-dispatcher", "DISPATCHER", true);
        UUID hub = hubRepository.findByCode("LK-UP").orElseThrow().getId();
        String active = String.format(INSERT_ASSIGNMENT, "NULL, NULL");

        // Unknown leg type, an end time without a reason, and a reason without an end time
        assertViolation(active, UUID.randomUUID(), "SIDEWAYS", 1, courier, dispatcher, hub);
        assertViolation(String.format(INSERT_ASSIGNMENT, "now(), NULL"), UUID.randomUUID(), "PICKUP", 1, courier,
                dispatcher, hub);
        assertViolation(String.format(INSERT_ASSIGNMENT, "NULL, 'REASSIGNED'"), UUID.randomUUID(), "PICKUP", 1,
                courier, dispatcher, hub);
        // Unknown courier or hub
        assertViolation(active, UUID.randomUUID(), "PICKUP", 1, UUID.randomUUID(), dispatcher, hub);
        assertViolation(active, UUID.randomUUID(), "PICKUP", 1, courier, dispatcher, UUID.randomUUID());
    }

    private UUID insertUser(String prefix, String roleName, boolean enabled) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, ?, ?)",
                id, prefix + "-" + id + "@example.com", "Test " + prefix, enabled, Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, roleName);
        return id;
    }
}

