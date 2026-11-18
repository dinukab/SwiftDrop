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
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.Actor;
import com.swiftdrop.delivery.application.CourierHubService;
import com.swiftdrop.delivery.application.DeliveryProofService;
import com.swiftdrop.delivery.application.FinalMileService;
import com.swiftdrop.delivery.application.ParcelStatusService;
import com.swiftdrop.delivery.application.StatusChange;
import com.swiftdrop.dispatch.domain.CourierProfile;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.dispatch.domain.VehicleType;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * The multi-courier hub journey of ADR 0007 on real PostgreSQL with the real beans: different couriers for
 * pickup, hub transport and final delivery, each leg assigned by the dispatcher of its source hub.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class MultiLegJourneyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final Instant NOW = Instant.now();
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @Autowired
    private DispatchService dispatch;
    @Autowired
    private HubService hubs;
    @Autowired
    private ParcelCreation creation;
    @Autowired
    private ParcelStatusService statuses;
    @Autowired
    private CourierHubService hubArrivals;
    @Autowired
    private FinalMileService finalMile;
    @Autowired
    private DeliveryProofService proofs;
    @Autowired
    private CourierProfileRepository profiles;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID merchantId;
    private UUID uvaDispatcher;
    private UUID colomboDispatcher;
    private UUID southernDispatcher;
    private UUID pickupCourier;
    private UUID uvaToColomboCourier;
    private UUID colomboToSouthernCourier;
    private UUID finalCourier;
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
        southernDispatcher = user("southern-dispatcher", "DISPATCHER");
        hubs.assignDispatcher(uvaDispatcher, "LK-UP");
        hubs.assignDispatcher(colomboDispatcher, "LK-WP");
        hubs.assignDispatcher(southernDispatcher, "LK-SP");
        pickupCourier = courier("pickup");
        uvaToColomboCourier = courier("uva-colombo");
        colomboToSouthernCourier = courier("colombo-southern");
        finalCourier = courier("final");
    }

    @Test
    void aParcelFromUvaToSouthernTravelsWithFourCouriersAndEachHubAssignsItsOwnLeg() {
        UUID parcel = parcel("ORD-1", "LK-UP", "LK-SP");

        // 1. The Uva dispatcher assigns the pickup courier; the four legs are planned
        dispatch.assign(parcel, pickupCourier, uvaDispatcher);
        assertThat(legTypes(parcel)).containsExactly("PICKUP", "INTER_HUB", "INTER_HUB", "FINAL_MILE");
        assertThat(heldBy(parcel)).isEqualTo(pickupCourier);
        assertThat(legCourier(parcel, 1)).isEqualTo(pickupCourier);
        // Only the holder can act on the parcel
        assertThatThrownBy(() -> move(parcel, uvaToColomboCourier, ParcelStatus.PICKED_UP))
                .isInstanceOf(ParcelNotFoundException.class);

        // 2. Pickup and arrival at the origin hub: the hub must be named, and must be the right one
        move(parcel, pickupCourier, ParcelStatus.PICKED_UP);
        assertThatThrownBy(() -> move(parcel, pickupCourier, ParcelStatus.HUB_ARRIVED))
                .isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> hubArrivals.confirmHubArrival(parcel, colomboHub, pickupCourier))
                .isInstanceOf(ParcelConflictException.class);
        hubArrivals.confirmHubArrival(parcel, uvaHub, pickupCourier);
        move(parcel, pickupCourier, ParcelStatus.HUB_SORTED);
        assertThat(legDone(parcel, 1)).isTrue();

        // 3. Only the dispatcher of the leg's source hub assigns it, and only once the previous leg is done
        assertThat(waitingLegSequences(uvaDispatcher)).containsExactly(2);
        assertThat(waitingLegSequences(colomboDispatcher)).isEmpty();
        UUID legToColombo = legId(parcel, 2);
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, legToColombo, uvaToColomboCourier, colomboDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, legId(parcel, 3), colomboToSouthernCourier,
                colomboDispatcher)).isInstanceOf(ParcelConflictException.class);
        dispatch.assignLeg(parcel, legToColombo, uvaToColomboCourier, uvaDispatcher);
        assertThat(heldBy(parcel)).isEqualTo(uvaToColomboCourier);
        // The pickup courier has let go of the parcel
        assertThatThrownBy(() -> move(parcel, pickupCourier, ParcelStatus.IN_TRANSIT))
                .isInstanceOf(ParcelNotFoundException.class);

        // 4. Uva to Colombo
        move(parcel, uvaToColomboCourier, ParcelStatus.IN_TRANSIT);
        hubArrivals.confirmHubArrival(parcel, colomboHub, uvaToColomboCourier);
        move(parcel, uvaToColomboCourier, ParcelStatus.HUB_SORTED);

        // 5. The Colombo dispatcher assigns the next leg
        assertThat(waitingLegSequences(colomboDispatcher)).containsExactly(3);
        assertThat(waitingLegSequences(uvaDispatcher)).isEmpty();
        dispatch.assignLeg(parcel, legId(parcel, 3), colomboToSouthernCourier, colomboDispatcher);
        move(parcel, colomboToSouthernCourier, ParcelStatus.IN_TRANSIT);
        hubArrivals.confirmHubArrival(parcel, southernHub, colomboToSouthernCourier);
        move(parcel, colomboToSouthernCourier, ParcelStatus.HUB_SORTED);

        // 6. The Southern dispatcher assigns the final delivery
        assertThat(waitingLegSequences(southernDispatcher)).containsExactly(4);
        dispatch.assignLeg(parcel, legId(parcel, 4), finalCourier, southernDispatcher);
        finalMile.pickupFromHub(parcel, finalCourier);
        assertThatThrownBy(() -> finalMile.completeDelivery(parcel, finalCourier))
                .as("delivery needs proof").isInstanceOf(ParcelConflictException.class);
        proofs.capture(parcel, Actor.courier(finalCourier), "Kumari Silva", JPEG);
        finalMile.completeDelivery(parcel, finalCourier);

        // Every leg is complete, the parcel is delivered, and each step is in the status history
        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel))
                .isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipment_legs WHERE parcel_id = ? AND completed_at IS NULL", Integer.class, parcel))
                .isZero();
        assertThat(jdbc.queryForList("SELECT to_status FROM parcel_status_history WHERE parcel_id = ? ORDER BY sequence",
                String.class, parcel)).containsExactly("CREATED", "PICKED_UP", "HUB_ARRIVED", "HUB_SORTED",
                        "IN_TRANSIT", "HUB_ARRIVED", "HUB_SORTED", "IN_TRANSIT", "HUB_ARRIVED", "HUB_SORTED",
                        "OUT_FOR_DELIVERY", "DELIVERED");

        // The assignment history has one active assignment per leg, with the courier of each
        List<DispatchService.AssignmentHistoryEntry> history = dispatch.assignmentHistory(parcel, southernDispatcher);
        assertThat(history).extracting(DispatchService.AssignmentHistoryEntry::legSequence)
                .containsExactly(1, 2, 3, 4);
        assertThat(history).extracting(DispatchService.AssignmentHistoryEntry::courierUserId)
                .containsExactly(pickupCourier, uvaToColomboCourier, colomboToSouthernCourier, finalCourier);
        assertThat(history).extracting(DispatchService.AssignmentHistoryEntry::hubCode)
                .containsExactly("LK-UP", "LK-UP", "LK-WP", "LK-SP");
        assertThat(history).allMatch(entry -> entry.endedAt() == null);
        // A hub the parcel never touched sees nothing
        UUID northernDispatcher = user("northern-dispatcher", "DISPATCHER");
        hubs.assignDispatcher(northernDispatcher, "LK-NP");
        assertThatThrownBy(() -> dispatch.assignmentHistory(parcel, northernDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void aParcelWithinOneProvinceNeedsOnlyAPickupAndAFinalLeg() {
        UUID parcel = parcel("ORD-2", "LK-UP", "LK-UP");

        dispatch.assign(parcel, pickupCourier, uvaDispatcher);
        assertThat(legTypes(parcel)).containsExactly("PICKUP", "FINAL_MILE");

        move(parcel, pickupCourier, ParcelStatus.PICKED_UP);
        hubArrivals.confirmHubArrival(parcel, uvaHub, pickupCourier);
        move(parcel, pickupCourier, ParcelStatus.HUB_SORTED);

        assertThat(waitingLegSequences(uvaDispatcher)).containsExactly(2);
        dispatch.assignLeg(parcel, legId(parcel, 2), finalCourier, uvaDispatcher);
        finalMile.pickupFromHub(parcel, finalCourier);
        proofs.capture(parcel, Actor.courier(finalCourier), "Kumari Silva", JPEG);
        finalMile.completeDelivery(parcel, finalCourier);

        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel))
                .isEqualTo("DELIVERED");
        assertThat(dispatch.assignmentHistory(parcel, uvaDispatcher)).hasSize(2);
    }

    @Test
    void aLegCanBeReassignedUntilItsCourierStartsAndOnlyThatLegIsAffected() {
        UUID parcel = parcel("ORD-3", "LK-UP", "LK-SP");
        dispatch.assign(parcel, pickupCourier, uvaDispatcher);
        move(parcel, pickupCourier, ParcelStatus.PICKED_UP);
        hubArrivals.confirmHubArrival(parcel, uvaHub, pickupCourier);
        move(parcel, pickupCourier, ParcelStatus.HUB_SORTED);
        UUID leg = legId(parcel, 2);

        dispatch.assignLeg(parcel, leg, uvaToColomboCourier, uvaDispatcher);
        dispatch.assignLeg(parcel, leg, colomboToSouthernCourier, uvaDispatcher);

        assertThat(heldBy(parcel)).isEqualTo(colomboToSouthernCourier);
        List<DispatchService.AssignmentHistoryEntry> history = dispatch.assignmentHistory(parcel, uvaDispatcher);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).legSequence()).isEqualTo(1);
        assertThat(history.get(0).endReason()).isNull();
        assertThat(history.get(1).legSequence()).isEqualTo(2);
        assertThat(history.get(1).endReason()).isEqualTo("REASSIGNED");
        assertThat(history.get(2).endReason()).isNull();

        // Once the new courier sets off the leg is theirs
        move(parcel, colomboToSouthernCourier, ParcelStatus.IN_TRANSIT);
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, leg, uvaToColomboCourier, uvaDispatcher))
                .isInstanceOf(ParcelConflictException.class);
    }

    @Test
    void aPlainParcelWithoutLegsKeepsTheSingleCourierBehaviour() {
        // A pickup zone that is not a province has no hub: no legs, and the old rules apply
        UUID parcel = parcelWithRawZones("ORD-4", zoneId("LK-UP"), UUID.randomUUID());

        dispatch.assign(parcel, pickupCourier, uvaDispatcher);

        assertThat(legTypes(parcel)).isEmpty();
        move(parcel, pickupCourier, ParcelStatus.PICKED_UP);
        move(parcel, pickupCourier, ParcelStatus.HUB_ARRIVED);
        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel))
                .isEqualTo("HUB_ARRIVED");
    }

    // --- helpers ---

    private void move(UUID parcel, UUID courier, ParcelStatus target) {
        statuses.changeStatus(parcel, new StatusChange(target, null, null), Actor.courier(courier));
    }

    private UUID heldBy(UUID parcel) {
        return jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, parcel);
    }

    private List<String> legTypes(UUID parcel) {
        return jdbc.queryForList("SELECT leg_type FROM shipment_legs WHERE parcel_id = ? ORDER BY sequence",
                String.class, parcel);
    }

    private UUID legId(UUID parcel, int sequence) {
        return jdbc.queryForObject("SELECT id FROM shipment_legs WHERE parcel_id = ? AND sequence = ?", UUID.class,
                parcel, sequence);
    }

    private UUID legCourier(UUID parcel, int sequence) {
        return jdbc.queryForObject("SELECT assigned_courier_id FROM shipment_legs WHERE parcel_id = ? AND sequence = ?",
                UUID.class, parcel, sequence);
    }

    private boolean legDone(UUID parcel, int sequence) {
        return jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM shipment_legs WHERE parcel_id = ? "
                + "AND sequence = ?", Boolean.class, parcel, sequence);
    }

    /** The waiting legs of this test's parcels only (other tests leave their own rows behind). */
    private List<Integer> waitingLegSequences(UUID dispatcher) {
        return dispatch.waitingLegs(dispatcher, 0, 100).items().stream()
                .filter(leg -> jdbc.queryForObject("SELECT merchant_id FROM parcels WHERE id = ?", UUID.class,
                        leg.parcelId()).equals(merchantId))
                .map(DispatchService.WaitingLegEntry::sequence)
                .toList();
    }

    private UUID parcel(String reference, String pickupProvince, String dropoffProvince) {
        return parcelWithRawZones(reference, zoneId(pickupProvince), zoneId(dropoffProvince));
    }

    private UUID parcelWithRawZones(String reference, UUID pickupZone, UUID dropoffZone) {
        return creation.createMerchantParcel(new NewMerchantParcel(merchantId, reference,
                contact("Pickup", pickupZone), contact("Dropoff", dropoffZone), ParcelSize.SMALL, 1000, null))
                .parcelId();
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
