package com.swiftdrop.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.swiftdrop.notification.application.DeadLetter;
import com.swiftdrop.notification.application.DeadLetterReplayService;

/**
 * The recovery path end to end with the consumer running: a message the consumer cannot read is dead-lettered by the
 * broker (real death headers), a valid dead letter replayed after the fact creates exactly one notification, and
 * replaying the same event again creates no second one.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.notifications.enabled=true"
})
@Testcontainers
class DeadLetterReplayFlowIT {

    private static final String SOURCE = "notification.parcel-status-changed";
    private static final String DEAD_LETTER = SOURCE + ".dlq";
    private static final String RABBIT_USER = "dlq-flow";
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
    private DeadLetterReplayService replays;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID admin;
    private UUID customer;

    @BeforeEach
    void setUp() {
        rabbitAdmin.purgeQueue(DEAD_LETTER, false);
        jdbc.update("DELETE FROM notification_dead_letter_replays");
        admin = user("admin", "ADMIN");
        customer = user("customer", "CUSTOMER");
    }

    @Test
    void aMessageTheConsumerCannotReadIsDeadLetteredWithBrokerHeadersAndReturnsIfReplayed() throws Exception {
        rabbitTemplate.send("", SOURCE, json("this is not json", null));

        awaitTrue(() -> replays.overview().depth() == 1);
        DeadLetter letter = replays.overview().oldest().get(0);
        assertThat(letter.eventId()).isNull();
        assertThat(letter.reason()).isEqualTo("rejected");
        assertThat(letter.deathCount()).isEqualTo(1);
        assertThat(letter.deadLetteredAt()).isNotNull();

        // Still unreadable after the replay: the consumer rejects it again and it returns to the dead-letter queue
        DeadLetterReplayService.Outcome outcome = replays.replay(admin, 5);
        assertThat(outcome.replayed()).hasSize(1);
        awaitTrue(() -> replays.overview().depth() == 1);
        assertThat(count("SELECT COUNT(*) FROM notification_dead_letter_replays")).isEqualTo(1);
    }

    @Test
    void aValidDeadLetterReplayedAfterTheFixCreatesExactlyOneNotification() throws Exception {
        UUID event = UUID.randomUUID();
        UUID parcel = UUID.randomUUID();
        String body = "{\"eventId\":\"" + event + "\",\"data\":{\"parcelId\":\"" + parcel
                + "\",\"trackingNumber\":\"SD7K3M9Q2XWB\",\"newStatus\":\"DELIVERED\",\"source\":\"CUSTOMER\","
                + "\"ownerCustomerId\":\"" + customer + "\"}}";

        // The event sits in the dead-letter queue, as if the consumer had failed on it earlier
        rabbitTemplate.send("", DEAD_LETTER, json(body, event));
        awaitTrue(() -> replays.overview().depth() == 1);
        assertThat(notifications(customer)).isZero();

        replays.replay(admin, 5);
        awaitTrue(() -> notifications(customer) == 1);
        awaitTrue(() -> replays.overview().depth() == 0);
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE user_id = ?", String.class, customer))
                .contains("SD7K3M9Q2XWB");

        // The same event dead-letters and is replayed a second time: the consumer ignores the duplicate
        rabbitTemplate.send("", DEAD_LETTER, json(body, event));
        awaitTrue(() -> replays.overview().depth() == 1);
        replays.replay(admin, 5);
        awaitTrue(() -> replays.overview().depth() == 0);
        Thread.sleep(1000); // time for the consumer to have handled the duplicate
        assertThat(notifications(customer)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM notification_dead_letter_replays WHERE event_id = '" + event + "'"))
                .isEqualTo(2);
    }

    private Message json(String body, UUID eventId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        if (eventId != null) {
            properties.setMessageId(eventId.toString());
        }
        return MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).andProperties(properties).build();
    }

    private int notifications(UUID userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class, userId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
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

    private UUID user(String prefix, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, ?)",
                id, prefix + "-" + id + "@example.com", "Test " + prefix, Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, role);
        return id;
    }
}
