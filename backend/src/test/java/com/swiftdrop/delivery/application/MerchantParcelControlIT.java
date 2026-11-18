package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.delivery.api.MerchantParcelControl;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.TrackingNumberGenerator;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * A merchant holds, releases and cancels its own delivery against real PostgreSQL: the widened
 * CHECK constraints accept the new statuses, status/history/outbox commit together, events reach
 * in-process listeners only after commit, and a hold racing a courier pickup has exactly one
 * winner (ADR 0006 amendment, ADR 0004 §3).
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
@Import(MerchantParcelControlIT.CommittedEvents.class)
class MerchantParcelControlIT {

    private static final String REFERENCE = "ORD-10045";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MerchantParcelControl control;
    @Autowired
    private ParcelStatusService statusService;
    @Autowired
    private ParcelEventRecorder recorder;
    @Autowired
    private ParcelRepository parcels;
    @Autowired
    private ParcelEventSerializer serializer;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CommittedEvents committedEvents;

    // No pricing implementation on this branch; booking needs one to start
    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID merchantId;
    private UUID merchantUserId;
    private UUID courierId;
    private UUID parcelId;
    private String reference;

    @BeforeEach
    void createMerchantParcel() {
        merchantId = UUID.randomUUID();
        merchantUserId = insertUser("merchant");
        courierId = insertUser("courier");
        reference = REFERENCE + "-" + UUID.randomUUID();
        parcelId = transactions.execute(status -> {
            Parcel parcel = parcels.save(Parcel.forMerchant(new TrackingNumberGenerator().next(), merchantId,
                    reference, address("Colombo"), address("Kandy"), ParcelSize.SMALL, 1000, null,
                    new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), Instant.now()));
            parcels.flush();
            recorder.record(parcel, null, Actor.merchantApi(), null, null, Instant.now());
            return parcel.getId();
        });
        committedEvents.clear();
    }

    @Test
    void pauseResumeAndCancelAreStoredWithHistoryAndEvents() {
        control.pause(merchantId, reference, merchantUserId, "Still packing");
        assertThat(status()).isEqualTo("PAUSED");

        control.resume(merchantId, reference, merchantUserId, null);
        assertThat(status()).isEqualTo("CREATED");

        control.pause(merchantId, reference, merchantUserId, null);
        ParcelStatus last = control.cancel(merchantId, reference, merchantUserId, "Out of stock");

        assertThat(last).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT last_sequence FROM parcels WHERE id = ?", Integer.class, parcelId))
                .isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT to_status FROM parcel_status_history WHERE parcel_id = ? ORDER BY sequence",
                String.class, parcelId))
                .containsExactly("CREATED", "PAUSED", "CREATED", "PAUSED", "CANCELLED");
        assertThat(jdbc.queryForList(
                "SELECT note FROM parcel_status_history WHERE parcel_id = ? AND actor_role = 'MERCHANT' "
                        + "AND note IS NOT NULL ORDER BY sequence", String.class, parcelId))
                .containsExactly("Still packing", "Out of stock");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM parcel_outbox_events WHERE parcel_id = ?", Integer.class, parcelId))
                .isEqualTo(5);
    }

    @Test
    void eventsCarryTheOwnerAndOrderReferenceButNeverTheReasonText() {
        control.pause(merchantId, reference, merchantUserId, "Customer asked to wait");

        List<ParcelStatusChanged> received = committedEvents.received();
        assertThat(received).hasSize(1);
        ParcelStatusChanged event = received.getFirst();
        assertThat(event.data().newStatus()).isEqualTo(ParcelStatus.PAUSED);
        assertThat(event.data().previousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(event.data().sequence()).isEqualTo(2);
        assertThat(event.data().ownerMerchantId()).isEqualTo(merchantId);
        assertThat(event.data().merchantOrderReference()).isEqualTo(reference);

        String payload = jdbc.queryForObject("SELECT payload::text FROM parcel_outbox_events WHERE event_id = ?",
                String.class, event.eventId());
        assertThat(serializer.fromJson(payload)).isEqualTo(event);
        assertThat(payload).doesNotContain("Customer asked to wait");
    }

    @Test
    void aRolledBackHoldLeavesNoTraceAndPublishesNothing() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            control.pause(merchantId, reference, merchantUserId, null);
            throw new IllegalStateException("simulated failure after the change");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(status()).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcel_status_history WHERE parcel_id = ?",
                Integer.class, parcelId)).isEqualTo(1);
        assertThat(committedEvents.received()).isEmpty();
    }

    @Test
    void anotherMerchantCannotSeeOrTouchTheOrder() {
        assertThatThrownBy(() -> control.cancel(UUID.randomUUID(), reference, merchantUserId, null))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThat(status()).isEqualTo("CREATED");
    }

    @Test
    void aHeldParcelCannotBePickedUpAndAPickedUpParcelCannotBeHeld() {
        jdbc.update("UPDATE parcels SET assigned_courier_id = ?, assigned_at = now() WHERE id = ?", courierId, parcelId);
        control.pause(merchantId, reference, merchantUserId, null);

        assertThatThrownBy(() -> statusService.changeStatus(parcelId,
                new StatusChange(ParcelStatus.PICKED_UP, null, null), Actor.courier(courierId)))
                .isInstanceOf(ParcelConflictException.class);
        assertThat(status()).isEqualTo("PAUSED");

        control.resume(merchantId, reference, merchantUserId, null);
        statusService.changeStatus(parcelId, new StatusChange(ParcelStatus.PICKED_UP, null, null),
                Actor.courier(courierId));

        assertThatThrownBy(() -> control.pause(merchantId, reference, merchantUserId, null))
                .isInstanceOf(ParcelConflictException.class);
        assertThatThrownBy(() -> control.cancel(merchantId, reference, merchantUserId, null))
                .isInstanceOf(ParcelConflictException.class);
        assertThat(status()).isEqualTo("PICKED_UP");
    }

    @Test
    void theCourierRuleSurvivesTheWidenedConstraint() {
        // A held or cancelled parcel may have no courier, but pickup still needs one
        control.pause(merchantId, reference, merchantUserId, null);
        assertThat(status()).isEqualTo("PAUSED");

        assertThatThrownBy(() -> jdbc.update("UPDATE parcels SET status = 'PICKED_UP' WHERE id = ?", parcelId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aHoldRacingACourierPickupHasExactlyOneWinner() throws Exception {
        jdbc.update("UPDATE parcels SET assigned_courier_id = ?, assigned_at = now() WHERE id = ?", courierId, parcelId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> hold = pool.submit(raced(start,
                    () -> control.pause(merchantId, reference, merchantUserId, null)));
            Future<Boolean> pickup = pool.submit(raced(start,
                    () -> statusService.changeStatus(parcelId, new StatusChange(ParcelStatus.PICKED_UP, null, null),
                            Actor.courier(courierId))));
            start.countDown();

            boolean held = hold.get(30, TimeUnit.SECONDS);
            boolean pickedUp = pickup.get(30, TimeUnit.SECONDS);

            assertThat(held ^ pickedUp).as("exactly one of hold and pickup succeeds").isTrue();
            assertThat(status()).isEqualTo(held ? "PAUSED" : "PICKED_UP");
            // The sequence is gap-free and shared by no two changes
            assertThat(jdbc.queryForList("SELECT sequence FROM parcel_status_history WHERE parcel_id = ? ORDER BY sequence",
                    Integer.class, parcelId)).containsExactly(1, 2);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<Boolean> raced(CountDownLatch start, Runnable action) {
        return () -> {
            start.await();
            try {
                action.run();
                return true;
            } catch (ParcelConflictException lost) {
                return false;
            }
        };
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?", String.class, parcelId);
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, now())",
                id, name + "-" + id + "@example.com", "Test " + name);
        return id;
    }

    private static ParcelAddress address(String city) {
        return new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, city, UUID.randomUUID(), null);
    }

    /** Stands in for {@code tracking}: records events delivered after commit only. */
    @TestConfiguration
    static class CommittedEvents {

        private final List<ParcelStatusChanged> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener
        void onCommitted(ParcelStatusChanged event) {
            events.add(event);
        }

        List<ParcelStatusChanged> received() {
            return List.copyOf(events);
        }

        void clear() {
            events.clear();
        }
    }
}
