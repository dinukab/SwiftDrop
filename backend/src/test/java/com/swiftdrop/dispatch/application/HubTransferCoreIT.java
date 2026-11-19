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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelLegs;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.Actor;
import com.swiftdrop.delivery.application.CourierHubService;
import com.swiftdrop.delivery.application.ParcelStatusService;
import com.swiftdrop.delivery.application.StatusChange;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * Direct hub-to-hub transfer (ADR 0007 amendment) at the delivery level on real PostgreSQL: the migration, the
 * relaxed courier constraint, the DISPATCHER actor in the history, no courier or assignment row for a transfer,
 * and two simultaneous transfers of one leg.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class HubTransferCoreIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final Instant NOW = Instant.now();

    @Autowired
    private DispatchService dispatch;
    @Autowired
    private HubService hubs;
    @Autowired
    private ParcelLegs parcelLegs;
    @Autowired
    private ParcelCreation creation;
    @Autowired
    private ParcelStatusService statuses;
    @Autowired
    private CourierHubService hubArrivals;
    @Autowired
    private CourierProfileRepository profiles;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID merchantId;
    private UUID uvaDispatcher;
    private UUID colomboDispatcher;
    private UUID pickupCourier;
    private UUID uvaHub;
    private UUID colomboHub;
    private UUID southernHub;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("150.00"), UUID.randomUUID()));
        merchantId = UUID.randomUUID();
        uvaHub = hubId("LK-UP");
        colomboHub = hubId("LK-WP");
        southernHub = hubId("LK-SP");
        uvaDispatcher = user("uva-dispatcher", "DISPATCHER");
        colomboDispatcher = user("colombo-dispatcher", "DISPATCHER");
        hubs.assignDispatcher(uvaDispatcher, "LK-UP");
        hubs.assignDispatcher(colomboDispatcher, "LK-WP");
        pickupCourier = courier("pickup");
    }

    /** Uva to Southern, picked up and received at the Uva hub: the parcel is ready to be sent to Colombo. */
    private UUID parcelAtUvaHub(String reference) {
        UUID parcel = creation.createMerchantParcel(new NewMerchantParcel(merchantId, reference,
                contact("Pickup", zoneId("LK-UP")), contact("Dropoff", zoneId("LK-SP")), ParcelSize.SMALL, 1000, null))
                .parcelId();
        dispatch.assign(parcel, pickupCourier, uvaDispatcher);
        statuses.changeStatus(parcel, new StatusChange(ParcelStatus.PICKED_UP, null, null),
                Actor.courier(pickupCourier));
        hubArrivals.confirmHubArrival(parcel, uvaHub, pickupCourier);
        return parcel;
    }

    @Test
    void aTransferSendsTheParcelWithoutACourierAndOnlyTheReceiptMakesItArrive() {
        UUID parcel = parcelAtUvaHub("ORD-T1");
        UUID toColombo = legId(parcel, 2);
        int assignmentRows = assignmentRows(parcel);

        // The Uva hub has it ready to send; Colombo sees nothing coming yet
        assertThat(ready(uvaHub)).contains(parcel);
        assertThat(incoming(colomboHub)).doesNotContain(parcel);

        parcelLegs.transferLeg(parcel, toColombo, uvaDispatcher);

        // On its way with nobody holding it; no courier on the leg and no assignment row were created
        assertThat(status(parcel)).isEqualTo("IN_TRANSIT");
        assertThat(heldBy(parcel)).isNull();
        assertThat(legCourier(parcel, 2)).isNull();
        assertThat(assignmentRows(parcel)).isEqualTo(assignmentRows);
        assertThat(jdbc.queryForObject("SELECT transferred_by FROM shipment_legs WHERE id = ?", UUID.class, toColombo))
                .isEqualTo(uvaDispatcher);
        assertThat(jdbc.queryForObject("SELECT received_at IS NULL AND completed_at IS NULL FROM shipment_legs "
                + "WHERE id = ?", Boolean.class, toColombo)).isTrue();
        // Sent is not received: it shows as incoming at Colombo, the pickup courier lost access, and the final
        // leg cannot be given to a courier anywhere yet
        assertThat(ready(uvaHub)).doesNotContain(parcel);
        assertThat(incoming(colomboHub)).contains(parcel);
        assertThatThrownBy(() -> statuses.changeStatus(parcel, new StatusChange(ParcelStatus.HUB_ARRIVED, null, null),
                Actor.courier(pickupCourier))).isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> parcelLegs.transferLeg(parcel, toColombo, uvaDispatcher))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel has already been transferred");
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, legId(parcel, 4), pickupCourier, colomboDispatcher))
                .isInstanceOf(RuntimeException.class);

        // The receiving hub confirms: only now has it arrived
        parcelLegs.confirmLegReceipt(parcel, toColombo, colomboDispatcher);
        assertThat(status(parcel)).isEqualTo("HUB_ARRIVED");
        assertThat(heldBy(parcel)).isNull();
        assertThat(incoming(colomboHub)).doesNotContain(parcel);
        assertThat(ready(colomboHub)).contains(parcel);

        // The history names the dispatchers as actors of the two moves
        assertThat(jdbc.queryForList("SELECT to_status || ':' || actor_role FROM parcel_status_history "
                + "WHERE parcel_id = ? ORDER BY sequence DESC LIMIT 2", String.class, parcel))
                .containsExactly("HUB_ARRIVED:DISPATCHER", "IN_TRANSIT:DISPATCHER");
        // Still no courier anywhere on the hub-to-hub leg
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shipment_legs WHERE parcel_id = ? AND sequence = 2 "
                + "AND assigned_courier_id IS NOT NULL", Integer.class, parcel)).isZero();
    }

    @Test
    void twoSimultaneousTransfersOfOneLegSendTheParcelOnlyOnce() throws Exception {
        UUID parcel = parcelAtUvaHub("ORD-T2");
        UUID toColombo = legId(parcel, 2);
        UUID otherDispatcher = user("uva-dispatcher-2", "DISPATCHER");
        hubs.assignDispatcher(otherDispatcher, "LK-UP");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<String>> tasks = List.of(
                () -> attempt(go, parcel, toColombo, uvaDispatcher),
                () -> attempt(go, parcel, toColombo, otherDispatcher));
        List<Future<String>> futures = tasks.stream().map(pool::submit).toList();
        go.countDown();
        List<String> results = List.of(futures.get(0).get(), futures.get(1).get());
        pool.shutdown();

        assertThat(results).containsExactlyInAnyOrder("sent", "duplicate");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcel_status_history WHERE parcel_id = ? "
                + "AND to_status = 'IN_TRANSIT'", Integer.class, parcel)).isEqualTo(1);
    }

    private String attempt(CountDownLatch go, UUID parcel, UUID leg, UUID dispatcher) throws InterruptedException {
        go.await();
        try {
            parcelLegs.transferLeg(parcel, leg, dispatcher);
            return "sent";
        } catch (ParcelConflictException e) {
            return "duplicate";
        }
    }

    private int assignmentRows(UUID parcel) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ?", Integer.class, parcel);
    }

    private List<UUID> ready(UUID hub) {
        return parcelLegs.findReadyTransfers(hub, new PageRequest(0, 100)).items().stream()
                .map(waiting -> waiting.leg().parcelId()).toList();
    }

    private List<UUID> incoming(UUID hub) {
        return parcelLegs.findIncomingLegs(hub, new PageRequest(0, 100)).items().stream()
                .map(waiting -> waiting.leg().parcelId()).toList();
    }

    private String status(UUID parcel) {
        return jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel);
    }

    private UUID heldBy(UUID parcel) {
        return jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, parcel);
    }

    private UUID legId(UUID parcel, int sequence) {
        return jdbc.queryForObject("SELECT id FROM shipment_legs WHERE parcel_id = ? AND sequence = ?", UUID.class,
                parcel, sequence);
    }

    private UUID legCourier(UUID parcel, int sequence) {
        return jdbc.queryForObject("SELECT assigned_courier_id FROM shipment_legs WHERE parcel_id = ? AND sequence = ?",
                UUID.class, parcel, sequence);
    }

    private UUID zoneId(String code) {
        return jdbc.queryForObject("SELECT id FROM zones WHERE code = ?", UUID.class, code);
    }

    private UUID hubId(String code) {
        return jdbc.queryForObject("SELECT id FROM hubs WHERE code = ?", UUID.class, code);
    }

    private static DeliveryContact contact(String city, UUID zoneId) {
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, zoneId, null);
    }

    private UUID courier(String name) {
        UUID id = user(name, "COURIER");
        profiles.saveAndFlush(new CourierProfile(id, "+94771234567", VehicleType.BIKE, NOW));
        return id;
    }

    private UUID user(String prefix, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, ?)",
                id, prefix + "-" + id + "@example.com", "Test " + prefix, Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, role);
        return id;
    }
}
