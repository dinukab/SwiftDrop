package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Hub scope of the dispatcher on real PostgreSQL with the real beans: a dispatcher sees and assigns
 * only the parcels waiting for pickup in their own hub's province, whatever else is in the system.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class DispatchHubScopeIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final Instant NOW = Instant.now();

    @Autowired
    private DispatchService dispatch;
    @Autowired
    private HubService hubs;
    @Autowired
    private ParcelCreation creation;
    @Autowired
    private CourierProfileRepository profiles;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID merchantId;
    private UUID uvaDispatcher;
    private UUID southernDispatcher;
    private UUID courier;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("150.00"), UUID.randomUUID()));
        merchantId = UUID.randomUUID();
        uvaDispatcher = insertUser("uva-dispatcher", "DISPATCHER");
        southernDispatcher = insertUser("southern-dispatcher", "DISPATCHER");
        hubs.assignDispatcher(uvaDispatcher, "LK-UP");
        hubs.assignDispatcher(southernDispatcher, "LK-SP");
        courier = insertUser("courier", "COURIER");
        profiles.saveAndFlush(new CourierProfile(courier, "+94771234567", VehicleType.BIKE, NOW));
    }

    @Test
    void eachDispatcherSeesOnlyTheParcelsWaitingForPickupInTheirOwnHub() {
        UUID fromUva = parcel("ORD-UVA", "LK-UP", "LK-SP");
        UUID fromSouthern = parcel("ORD-SP", "LK-SP", "LK-UP");

        assertThat(queue(uvaDispatcher)).extracting(ParcelSummary::id).containsExactly(fromUva);
        assertThat(queue(southernDispatcher)).extracting(ParcelSummary::id).containsExactly(fromSouthern);
        assertThat(queue(uvaDispatcher).get(0).pickupProvinceCode()).isEqualTo("LK-UP");
    }

    @Test
    void aDispatcherAssignsInTheirOwnHubButNotInAnotherOne() {
        UUID fromUva = parcel("ORD-UVA", "LK-UP", "LK-SP");

        // The Southern dispatcher cannot even tell the parcel exists, and nothing is assigned
        assertThatThrownBy(() -> dispatch.assign(fromUva, courier, southernDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThat(jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, fromUva))
                .isNull();

        dispatch.assign(fromUva, courier, uvaDispatcher);

        assertThat(jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, fromUva))
                .isEqualTo(courier);
        assertThat(queue(uvaDispatcher)).isEmpty();
        // After pickup the parcel is delivery's to protect: assigning again is a conflict, not a hub matter
        jdbc.update("UPDATE parcels SET status = 'PICKED_UP' WHERE id = ?", fromUva);
        assertThatThrownBy(() -> dispatch.assign(fromUva, courier, uvaDispatcher))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void aDispatcherWithoutAHubIsRefused() {
        UUID unbound = insertUser("unbound-dispatcher", "DISPATCHER");
        UUID fromUva = parcel("ORD-UVA", "LK-UP", "LK-SP");

        assertThatThrownBy(() -> dispatch.unassigned(unbound, 0, 20))
                .isInstanceOf(DispatcherHubNotAssignedException.class);
        assertThatThrownBy(() -> dispatch.assign(fromUva, courier, unbound))
                .isInstanceOf(DispatcherHubNotAssignedException.class);
    }

    @Test
    void assignmentsAreRecordedAndAReassignmentEndsThePreviousOne() {
        UUID other = insertUser("courier-2", "COURIER");
        profiles.saveAndFlush(new CourierProfile(other, "+94771234568", VehicleType.CAR, NOW));
        UUID fromUva = parcel("ORD-UVA", "LK-UP", "LK-SP");

        dispatch.assign(fromUva, courier, uvaDispatcher);
        dispatch.assign(fromUva, courier, uvaDispatcher); // the same courier again changes nothing
        dispatch.assign(fromUva, other, uvaDispatcher);   // another courier: a reassignment

        List<DispatchService.AssignmentHistoryEntry> history = dispatch.assignmentHistory(fromUva, uvaDispatcher);
        assertThat(history).hasSize(2);
        assertThat(history.get(0).courierUserId()).isEqualTo(courier);
        assertThat(history.get(0).endReason()).isEqualTo("REASSIGNED");
        assertThat(history.get(0).hubCode()).isEqualTo("LK-UP");
        assertThat(history.get(1).courierUserId()).isEqualTo(other);
        assertThat(history.get(1).endedAt()).isNull();
        assertThat(history.get(1).dispatcherName()).startsWith("Test uva-dispatcher");
        assertThat(activeAssignments(fromUva)).containsExactly(other);

        // Another hub's dispatcher cannot read it
        assertThatThrownBy(() -> dispatch.assignmentHistory(fromUva, southernDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void aRefusedAssignmentLeavesNoHistory() {
        UUID fromUva = parcel("ORD-UVA", "LK-UP", "LK-SP");
        dispatch.assign(fromUva, courier, uvaDispatcher);
        jdbc.update("UPDATE parcels SET status = 'PICKED_UP' WHERE id = ?", fromUva);
        UUID other = insertUser("courier-3", "COURIER");
        profiles.saveAndFlush(new CourierProfile(other, "+94771234569", VehicleType.BIKE, NOW));

        assertThatThrownBy(() -> dispatch.assign(fromUva, other, uvaDispatcher))
                .isInstanceOf(ParcelConflictException.class);

        assertThat(dispatch.assignmentHistory(fromUva, uvaDispatcher)).hasSize(1);
        assertThat(activeAssignments(fromUva)).containsExactly(courier);
    }

    @Test
    void twoDispatchersAssigningTheSameParcelAtOnceLeaveExactlyOneActiveAssignment() throws Exception {
        UUID secondUvaDispatcher = insertUser("uva-dispatcher-2", "DISPATCHER");
        hubs.assignDispatcher(secondUvaDispatcher, "LK-UP");
        UUID other = insertUser("courier-race", "COURIER");
        profiles.saveAndFlush(new CourierProfile(other, "+94771234570", VehicleType.VAN, NOW));
        UUID fromUva = parcel("ORD-RACE", "LK-UP", "LK-SP");

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> results = List.of(
                    pool.submit(() -> race(start, fromUva, courier, uvaDispatcher)),
                    pool.submit(() -> race(start, fromUva, other, secondUvaDispatcher)));
            for (Future<Throwable> result : results) {
                // Reassignment while CREATED is allowed, so both may succeed; neither may fail otherwise
                assertThat(result.get(60, TimeUnit.SECONDS)).isNull();
            }
        } finally {
            pool.shutdownNow();
        }

        List<UUID> active = activeAssignments(fromUva);
        assertThat(active).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, fromUva))
                .isEqualTo(active.get(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ?",
                Integer.class, fromUva)).isEqualTo(2);
    }

    private Throwable race(CyclicBarrier start, UUID parcelId, UUID courierId, UUID dispatcherId) throws Exception {
        start.await(30, TimeUnit.SECONDS);
        try {
            dispatch.assign(parcelId, courierId, dispatcherId);
            return null;
        } catch (Throwable e) {
            return e;
        }
    }

    private List<UUID> activeAssignments(UUID parcelId) {
        return jdbc.queryForList(
                "SELECT courier_user_id FROM parcel_assignments WHERE parcel_id = ? AND ended_at IS NULL",
                UUID.class, parcelId);
    }

    private List<ParcelSummary> queue(UUID dispatcher) {
        return dispatch.unassigned(dispatcher, 0, 100).items().stream()
                .filter(summary -> merchantId.equals(summary.merchantId()))
                .toList();
    }

    private UUID parcel(String reference, String pickupProvince, String dropoffProvince) {
        return creation.createMerchantParcel(new NewMerchantParcel(merchantId, reference,
                contact("Pickup", zoneId(pickupProvince)), contact("Dropoff", zoneId(dropoffProvince)),
                ParcelSize.SMALL, 1000, null)).parcelId();
    }

    private UUID zoneId(String code) {
        return jdbc.queryForObject("SELECT id FROM zones WHERE code = ?", UUID.class, code);
    }

    private static DeliveryContact contact(String city, UUID zoneId) {
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, zoneId, null);
    }

    private UUID insertUser(String prefix, String roleName) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, ?)",
                id, prefix + "-" + id + "@example.com", "Test " + prefix, Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, roleName);
        return id;
    }
}
