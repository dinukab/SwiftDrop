package com.swiftdrop.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.swiftdrop.delivery.api.DeliveryContact;
import com.swiftdrop.delivery.api.NewMerchantParcel;
import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.delivery.api.ParcelCreation;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.Actor;
import com.swiftdrop.delivery.application.Booking;
import com.swiftdrop.delivery.application.ParcelBookingService;
import com.swiftdrop.delivery.application.ParcelStatusService;
import com.swiftdrop.delivery.application.StatusChange;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * The whole notification chain on real PostgreSQL and RabbitMQ (PROGRESS 9.7): a courier changes a customer's parcel
 * status, the change is written to the outbox, the relay publishes it, the consumer stores a mock SMS, and the
 * customer's inbox shows it. Statuses that do not notify and merchant parcels leave no message.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.messaging.relay.enabled=true",
        "swiftdrop.messaging.relay.interval-ms=200",
        "swiftdrop.notifications.enabled=true"
})
@Testcontainers
class NotificationFlowIT {

    private static final String RABBIT_USER = "flow-test";
    private static final String RABBIT_PASSWORD = UUID.randomUUID().toString();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Container
    static GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:4.3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", RABBIT_USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", RABBIT_PASSWORD)
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> RABBIT_USER);
        registry.add("spring.rabbitmq.password", () -> RABBIT_PASSWORD);
    }

    private static final Instant NOW = Instant.now();

    @Autowired
    private ParcelBookingService booking;
    @Autowired
    private ParcelCreation creation;
    @Autowired
    private ParcelAssignment assignment;
    @Autowired
    private ParcelStatusService statuses;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private UUID customerId;
    private UUID courierId;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("650.00"), "LKR", new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("150.00"), UUID.randomUUID()));
        customerId = user("customer", "CUSTOMER");
        courierId = user("courier", "COURIER");
    }

    @Test
    void aCustomersStatusChangesReachTheirInboxThroughTheRelayAndRabbitMq() throws Exception {
        UUID parcel = booking.bookForCustomer(customerId, new Booking(contact(), contact(), ParcelSize.SMALL, 1000, null))
                .getId();
        assignment.assignCourier(parcel, courierId, UUID.randomUUID());

        move(parcel, ParcelStatus.PICKED_UP);
        move(parcel, ParcelStatus.HUB_ARRIVED);
        move(parcel, ParcelStatus.OUT_FOR_DELIVERY);

        // Picked up and out for delivery notify; the hub arrival and the booking do not
        awaitTrue(() -> count(customerId) == 2);
        List<String> statuses = jdbc.queryForList(
                "SELECT status FROM notifications WHERE user_id = ? ORDER BY created_at", String.class, customerId);
        assertThat(statuses).containsExactlyInAnyOrder("PICKED_UP", "OUT_FOR_DELIVERY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND parcel_id = ?",
                Integer.class, customerId, parcel)).isEqualTo(2);
        // The outbox rows were published, in order
        awaitTrue(() -> jdbc.queryForObject("SELECT COUNT(*) FROM parcel_outbox_events WHERE parcel_id = ? "
                + "AND published_at IS NULL", Integer.class, parcel) == 0);
    }

    @Test
    void aMerchantParcelLeavesNoNotification() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID parcel = creation.createMerchantParcel(new NewMerchantParcel(merchantId, "ORD-N1", contact(), contact(),
                ParcelSize.SMALL, 1000, null)).parcelId();
        assignment.assignCourier(parcel, courierId, UUID.randomUUID());
        move(parcel, ParcelStatus.PICKED_UP);

        awaitTrue(() -> jdbc.queryForObject("SELECT COUNT(*) FROM parcel_outbox_events WHERE parcel_id = ? "
                + "AND published_at IS NULL", Integer.class, parcel) == 0);
        // Give the consumer time to have handled the published events before asserting there is nothing
        Thread.sleep(1500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE parcel_id = ?", Integer.class,
                parcel)).isZero();
    }

    private void move(UUID parcel, ParcelStatus target) {
        statuses.changeStatus(parcel, new StatusChange(target, null, null), Actor.courier(courierId));
    }

    private int count(UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class, userId);
    }

    private static void awaitTrue(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(condition.get()).as("condition within 30 seconds").isTrue();
    }

    private DeliveryContact contact() {
        UUID zone = jdbc.queryForObject("SELECT id FROM zones WHERE code = 'LK-UP'", UUID.class);
        return new DeliveryContact("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Badulla", zone, null);
    }

    private UUID user(String prefix, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, ?)",
                id, prefix + "-" + id + "@example.com", "Test " + prefix, Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, role);
        return id;
    }
}
