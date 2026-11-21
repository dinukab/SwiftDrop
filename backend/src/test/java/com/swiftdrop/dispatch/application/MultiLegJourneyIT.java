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
 * The hub journey of ADR 0007 (with the direct hub-to-hub transfer amendment) on real PostgreSQL with the real
 * beans: couriers only for pickup and final delivery; between hubs the sending dispatcher transfers and the
 * receiving dispatcher confirms receipt.
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
    private UUID finalCourier;
    private UUID otherCourier;
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
        finalCourier = courier("final");
        otherCourier = courier("other");
    }

    @Test
    void aParcelFromUvaToSouthernMovesBetweenHubsByDirectTransfersAndOnlyPickupAndDeliveryHaveCouriers() {
        UUID parcel = parcel("ORD-1", "LK-UP", "LK-SP");

        // Stage 1: the Uva dispatcher assigns the pickup courier; the four legs are planned
        dispatch.assign(parcel, pickupCourier, uvaDispatcher);
        assertThat(legTypes(parcel)).containsExactly("PICKUP", "INTER_HUB", "INTER_HUB", "FINAL_MILE");
        assertThat(heldBy(parcel)).isEqualTo(pickupCourier);
        move(parcel, pickupCourier, ParcelStatus.PICKED_UP);

        // Not ready to send before the parcel was received at the sending hub
        UUID toColombo = legId(parcel, 2);
        UUID toSouthern = legId(parcel, 3);
        assertThatThrownBy(() -> dispatch.transfer(parcel, toColombo, uvaDispatcher))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The previous leg is not complete yet");
        // The courier names the right hub when arriving
        assertThatThrownBy(() -> hubArrivals.confirmHubArrival(parcel, colomboHub, pickupCourier))
                .isInstanceOf(ParcelConflictException.class);
        hubArrivals.confirmHubArrival(parcel, uvaHub, pickupCourier);
        assertThat(currentHub(pickupCourier)).isEqualTo(uvaHub);

        // Stage 2: Uva sends it to Colombo with no courier
        assertThat(ready(uvaDispatcher)).containsExactly(parcel);
        assertThat(ready(colomboDispatcher)).isEmpty();
        // Hub-to-hub legs are never given to a courier, and only the sending hub can send
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, toColombo, otherCourier, uvaDispatcher))
                .isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> dispatch.transfer(parcel, toColombo, colomboDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> dispatch.transfer(parcel, toColombo, southernDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
        int assignmentRows = assignmentRows(parcel);
        dispatch.transfer(parcel, toColombo, uvaDispatcher);
        assertThatThrownBy(() -> dispatch.transfer(parcel, toColombo, uvaDispatcher))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The parcel has already been transferred");
        assertThat(status(parcel)).isEqualTo("IN_TRANSIT");
        assertThat(heldBy(parcel)).isNull();
        assertThat(assignmentRows(parcel)).isEqualTo(assignmentRows);
        // The pickup courier no longer has the parcel
        assertThatThrownBy(() -> move(parcel, pickupCourier, ParcelStatus.HUB_ARRIVED))
                .isInstanceOf(ParcelNotFoundException.class);

        // Stage 3: sent is not received. It is incoming at Colombo and not ready to go on from there
        assertThat(ready(uvaDispatcher)).isEmpty();
        assertThat(incoming(colomboDispatcher)).containsExactly(parcel);
        assertThat(ready(colomboDispatcher)).isEmpty();
        assertThatThrownBy(() -> dispatch.transfer(parcel, toSouthern, colomboDispatcher))
                .isInstanceOf(ParcelConflictException.class);
        // Only the receiving hub confirms; neither the sender nor another hub can
        assertThatThrownBy(() -> dispatch.confirmReceipt(parcel, toColombo, uvaDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> dispatch.confirmReceipt(parcel, toColombo, southernDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
        dispatch.confirmReceipt(parcel, toColombo, colomboDispatcher);
        assertThat(status(parcel)).isEqualTo("HUB_ARRIVED");
        assertThat(incoming(colomboDispatcher)).isEmpty();
        assertThatThrownBy(() -> dispatch.confirmReceipt(parcel, toColombo, colomboDispatcher))
                .isInstanceOf(ParcelConflictException.class);

        // Every intermediate hub works the same way: Colombo sends it to Southern, Southern confirms
        assertThat(ready(colomboDispatcher)).containsExactly(parcel);
        dispatch.transfer(parcel, toSouthern, colomboDispatcher);
        assertThat(incoming(southernDispatcher)).containsExactly(parcel);
        // Stage 4 is closed until Southern has confirmed the receipt
        UUID finalLeg = legId(parcel, 4);
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, finalLeg, finalCourier, southernDispatcher))
                .isInstanceOf(ParcelConflictException.class).hasMessage("The previous leg is not complete yet");
        assertThat(waitingFinalLegs(southernDispatcher)).isEmpty();
        dispatch.confirmReceipt(parcel, toSouthern, southernDispatcher);

        // Stage 4: the Southern dispatcher assigns the final-mile courier, who delivers
        assertThat(waitingFinalLegs(southernDispatcher)).containsExactly(parcel);
        UUID uvaBased = courier("uva-based");
        jdbc.update("UPDATE couriers SET home_hub_id = ? WHERE user_id = ?", uvaHub, uvaBased);
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, finalLeg, uvaBased, southernDispatcher))
                .isInstanceOf(CourierNotEligibleException.class).hasMessage("Courier is not based at this hub");
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, finalLeg, finalCourier, colomboDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
        dispatch.assignLeg(parcel, finalLeg, finalCourier, southernDispatcher);
        assertThat(heldBy(parcel)).isEqualTo(finalCourier);
        finalMile.pickupFromHub(parcel, finalCourier);
        assertThatThrownBy(() -> finalMile.completeDelivery(parcel, finalCourier))
                .as("delivery needs proof").isInstanceOf(ParcelConflictException.class);
        proofs.capture(parcel, Actor.courier(finalCourier), "Kumari Silva", JPEG);
        finalMile.completeDelivery(parcel, finalCourier);

        // Delivered; every leg is complete; the status history names the dispatchers for the hub moves
        assertThat(status(parcel)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipment_legs WHERE parcel_id = ? AND completed_at IS NULL", Integer.class, parcel))
                .isZero();
        assertThat(jdbc.queryForList("SELECT to_status || ':' || actor_role FROM parcel_status_history "
                + "WHERE parcel_id = ? ORDER BY sequence", String.class, parcel)).containsExactly(
                        "CREATED:MERCHANT", "PICKED_UP:COURIER", "HUB_ARRIVED:COURIER", "IN_TRANSIT:DISPATCHER",
                        "HUB_ARRIVED:DISPATCHER", "IN_TRANSIT:DISPATCHER", "HUB_ARRIVED:DISPATCHER",
                        "OUT_FOR_DELIVERY:COURIER", "DELIVERED:COURIER");
        // Transfer and receipt are recorded on the hub-to-hub legs, which never had a courier
        assertThat(jdbc.queryForObject("SELECT transferred_by = ? AND received_by = ? AND assigned_courier_id IS NULL "
                + "FROM shipment_legs WHERE parcel_id = ? AND sequence = 2", Boolean.class, uvaDispatcher,
                colomboDispatcher, parcel)).isTrue();
        assertThat(jdbc.queryForObject("SELECT transferred_by = ? AND received_by = ? AND assigned_courier_id IS NULL "
                + "FROM shipment_legs WHERE parcel_id = ? AND sequence = 3", Boolean.class, colomboDispatcher,
                southernDispatcher, parcel)).isTrue();
        // Courier assignments exist only for the pickup and the final mile; dispatcher receipts moved no courier
        List<DispatchService.AssignmentHistoryEntry> history = dispatch.assignmentHistory(parcel, southernDispatcher);
        assertThat(history).extracting(DispatchService.AssignmentHistoryEntry::legSequence).containsExactly(1, 4);
        assertThat(history).extracting(DispatchService.AssignmentHistoryEntry::courierUserId)
                .containsExactly(pickupCourier, finalCourier);
        assertThat(currentHub(pickupCourier)).isEqualTo(uvaHub);
        // A hub the parcel never touched sees nothing
        UUID northernDispatcher = user("northern-dispatcher", "DISPATCHER");
        hubs.assignDispatcher(northernDispatcher, "LK-NP");
        assertThatThrownBy(() -> dispatch.assignmentHistory(parcel, northernDispatcher))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void aParcelWithinOneProvinceNeedsOnlyAPickupAndAFinalLegAndNoTransfer() {
        UUID parcel = parcel("ORD-2", "LK-UP", "LK-UP");

        dispatch.assign(parcel, pickupCourier, uvaDispatcher);
        assertThat(legTypes(parcel)).containsExactly("PICKUP", "FINAL_MILE");

        move(parcel, pickupCourier, ParcelStatus.PICKED_UP);
        hubArrivals.confirmHubArrival(parcel, uvaHub, pickupCourier);

        // Nothing to transfer: the same hub is both ends, and the final leg is assigned straight away
        assertThat(ready(uvaDispatcher)).isEmpty();
        assertThat(waitingFinalLegs(uvaDispatcher)).containsExactly(parcel);
        dispatch.assignLeg(parcel, legId(parcel, 2), finalCourier, uvaDispatcher);
        finalMile.pickupFromHub(parcel, finalCourier);
        proofs.capture(parcel, Actor.courier(finalCourier), "Kumari Silva", JPEG);
        finalMile.completeDelivery(parcel, finalCourier);

        assertThat(status(parcel)).isEqualTo("DELIVERED");
        assertThat(dispatch.assignmentHistory(parcel, uvaDispatcher)).hasSize(2);
    }

    @Test
    void aColomboParcelGoesStraightFromItsHubToTheDestinationHubWithOneTransfer() {
        UUID parcel = parcel("ORD-5", "LK-WP", "LK-SP");
        dispatch.assign(parcel, pickupCourier, colomboDispatcher);
        assertThat(legTypes(parcel)).containsExactly("PICKUP", "INTER_HUB", "FINAL_MILE");
        move(parcel, pickupCourier, ParcelStatus.PICKED_UP);
        hubArrivals.confirmHubArrival(parcel, colomboHub, pickupCourier);

        dispatch.transfer(parcel, legId(parcel, 2), colomboDispatcher);
        dispatch.confirmReceipt(parcel, legId(parcel, 2), southernDispatcher);

        assertThat(waitingFinalLegs(southernDispatcher)).containsExactly(parcel);
        dispatch.assignLeg(parcel, legId(parcel, 3), finalCourier, southernDispatcher);
        assertThat(heldBy(parcel)).isEqualTo(finalCourier);
    }

    @Test
    void aFinalLegCanBeReassignedUntilItsCourierStartsAndOnlyThatLegIsAffected() {
        UUID parcel = parcel("ORD-3", "LK-UP", "LK-UP");
        dispatch.assign(parcel, pickupCourier, uvaDispatcher);
        move(parcel, pickupCourier, ParcelStatus.PICKED_UP);
        hubArrivals.confirmHubArrival(parcel, uvaHub, pickupCourier);
        UUID leg = legId(parcel, 2);

        dispatch.assignLeg(parcel, leg, finalCourier, uvaDispatcher);
        dispatch.assignLeg(parcel, leg, otherCourier, uvaDispatcher);

        assertThat(heldBy(parcel)).isEqualTo(otherCourier);
        List<DispatchService.AssignmentHistoryEntry> history = dispatch.assignmentHistory(parcel, uvaDispatcher);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).legSequence()).isEqualTo(1);
        assertThat(history.get(0).endReason()).isNull();
        assertThat(history.get(1).legSequence()).isEqualTo(2);
        assertThat(history.get(1).endReason()).isEqualTo("REASSIGNED");
        assertThat(history.get(2).endReason()).isNull();

        // Once the new courier takes the parcel from the hub the leg is theirs
        finalMile.pickupFromHub(parcel, otherCourier);
        assertThatThrownBy(() -> dispatch.assignLeg(parcel, leg, finalCourier, uvaDispatcher))
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
        assertThat(status(parcel)).isEqualTo("HUB_ARRIVED");
    }

    // --- helpers ---

    private void move(UUID parcel, UUID courier, ParcelStatus target) {
        statuses.changeStatus(parcel, new StatusChange(target, null, null), Actor.courier(courier));
    }

    private UUID currentHub(UUID courierUserId) {
        return jdbc.queryForObject("SELECT current_hub_id FROM couriers WHERE user_id = ?", UUID.class, courierUserId);
    }

    private String status(UUID parcel) {
        return jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcel);
    }

    private UUID heldBy(UUID parcel) {
        return jdbc.queryForObject("SELECT assigned_courier_id FROM parcels WHERE id = ?", UUID.class, parcel);
    }

    private int assignmentRows(UUID parcel) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM parcel_assignments WHERE parcel_id = ?", Integer.class, parcel);
    }

    private List<String> legTypes(UUID parcel) {
        return jdbc.queryForList("SELECT leg_type FROM shipment_legs WHERE parcel_id = ? ORDER BY sequence",
                String.class, parcel);
    }

    private UUID legId(UUID parcel, int sequence) {
        return jdbc.queryForObject("SELECT id FROM shipment_legs WHERE parcel_id = ? AND sequence = ?", UUID.class,
                parcel, sequence);
    }

    /** Parcels of this test (other tests leave their own rows behind) waiting for their final-mile courier. */
    private List<UUID> waitingFinalLegs(UUID dispatcher) {
        return mine(dispatch.waitingLegs(dispatcher, 0, 100).items().stream()
                .map(DispatchService.WaitingLegEntry::parcelId).toList());
    }

    private List<UUID> ready(UUID dispatcher) {
        return mine(dispatch.readyTransfers(dispatcher, 0, 100).items().stream()
                .map(DispatchService.TransferEntry::parcelId).toList());
    }

    private List<UUID> incoming(UUID dispatcher) {
        return mine(dispatch.incomingTransfers(dispatcher, 0, 100).items().stream()
                .map(DispatchService.TransferEntry::parcelId).toList());
    }

    private List<UUID> mine(List<UUID> parcelIds) {
        return parcelIds.stream()
                .filter(id -> merchantId.equals(jdbc.queryForObject("SELECT merchant_id FROM parcels WHERE id = ?",
                        UUID.class, id)))
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
