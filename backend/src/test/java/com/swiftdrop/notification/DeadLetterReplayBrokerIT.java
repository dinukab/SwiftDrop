package com.swiftdrop.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
 * Dead-letter replay against a real broker, with the notification consumer switched off so that nothing else reads
 * the queues: messages move from the dead-letter queue to the source queue only after the broker confirmed them and
 * the audit row was saved, in order, within the limit, and a failure leaves the message in the dead-letter queue.
 */
@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password=",
        "swiftdrop.notifications.enabled=false"
})
@Testcontainers
class DeadLetterReplayBrokerIT {

    private static final String SOURCE = "notification.parcel-status-changed";
    private static final String DEAD_LETTER = SOURCE + ".dlq";
    private static final String RABBIT_USER = "dlq-test";
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

    @BeforeEach
    void setUp() {
        rabbitAdmin.initialize(); // declares the queues again if a test deleted one
        rabbitAdmin.purgeQueue(SOURCE, false);
        rabbitAdmin.purgeQueue(DEAD_LETTER, false);
        jdbc.update("DELETE FROM notification_dead_letter_replays");
        admin = user();
    }

    @Test
    void aReplayedMessageReachesTheSourceQueueIntactAndIsAudited() throws Exception {
        UUID event = UUID.randomUUID();
        String body = eventJson(event);
        deadLetter(event, body);
        awaitDepth(1);

        DeadLetterReplayService.Outcome outcome = replays.replay(admin, 10);

        assertThat(outcome.stopReason()).isNull();
        assertThat(outcome.replayed()).extracting(DeadLetter::eventId).containsExactly(event);
        Message delivered = rabbitTemplate.receive(SOURCE, 5000);
        assertThat(delivered).isNotNull();
        assertThat(new String(delivered.getBody(), StandardCharsets.UTF_8)).isEqualTo(body);
        assertThat(delivered.getMessageProperties().getMessageId()).isEqualTo(event.toString());
        awaitDepth(0);
        assertThat(jdbc.queryForList(
                "SELECT replayed_by FROM notification_dead_letter_replays WHERE event_id = ? AND queue = ?",
                UUID.class, event, SOURCE)).containsExactly(admin);
    }

    @Test
    void onlyTheRequestedNumberMovesAndTheOldestGoFirst() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        deadLetter(first, eventJson(first));
        deadLetter(second, eventJson(second));
        deadLetter(third, eventJson(third));
        awaitDepth(3);

        DeadLetterReplayService.Outcome outcome = replays.replay(admin, 2);

        assertThat(outcome.replayed()).extracting(DeadLetter::eventId).containsExactly(first, second);
        awaitDepth(1);
        assertThat(replays.overview().oldest()).extracting(DeadLetter::eventId).containsExactly(third);
        assertThat(count("SELECT COUNT(*) FROM notification_dead_letter_replays")).isEqualTo(2);
    }

    @Test
    void lookingAtTheQueueRemovesNothingAndKeepsTheOrder() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        deadLetter(first, eventJson(first));
        deadLetter(second, eventJson(second));
        awaitDepth(2);

        DeadLetterReplayService.Overview overview = replays.overview();
        assertThat(overview.depth()).isEqualTo(2);
        assertThat(overview.oldest()).extracting(DeadLetter::eventId).containsExactly(first, second);

        // After looking (several times), nothing is gone and the oldest is still the oldest
        for (int i = 0; i < 3; i++) {
            assertThat(replays.overview().oldest()).extracting(DeadLetter::eventId).containsExactly(first, second);
        }
        assertThat(replays.replay(admin, 1).replayed()).extracting(DeadLetter::eventId).containsExactly(first);
    }

    @Test
    void anUnparseableMessageIsReplayedAndAuditedWithoutAnEventId() throws Exception {
        deadLetter(null, "this is not json");
        awaitDepth(1);

        DeadLetterReplayService.Outcome outcome = replays.replay(admin, 5);

        assertThat(outcome.replayed()).hasSize(1);
        assertThat(outcome.replayed().get(0).eventId()).isNull();
        assertThat(count("SELECT COUNT(*) FROM notification_dead_letter_replays WHERE event_id IS NULL")).isEqualTo(1);
        assertThat(rabbitTemplate.receive(SOURCE, 5000)).isNotNull();
    }

    @Test
    void whenTheSourceQueueDoesNotExistTheMessageStaysAndNothingIsAudited() throws Exception {
        UUID event = UUID.randomUUID();
        deadLetter(event, eventJson(event));
        awaitDepth(1);
        rabbitAdmin.deleteQueue(SOURCE);

        DeadLetterReplayService.Outcome outcome = replays.replay(admin, 5);

        assertThat(outcome.replayed()).isEmpty();
        assertThat(outcome.stopReason()).isEqualTo("NOT_ROUTED");
        awaitDepth(1);
        assertThat(replays.overview().oldest()).extracting(DeadLetter::eventId).containsExactly(event);
        assertThat(count("SELECT COUNT(*) FROM notification_dead_letter_replays")).isZero();
    }

    @Test
    void whenTheAuditCannotBeSavedTheMessageStaysInTheDeadLetterQueue() throws Exception {
        UUID event = UUID.randomUUID();
        deadLetter(event, eventJson(event));
        awaitDepth(1);
        UUID unknownAdmin = UUID.randomUUID(); // not a user: the audit row violates its foreign key

        DeadLetterReplayService.Outcome outcome = replays.replay(unknownAdmin, 5);

        assertThat(outcome.replayed()).isEmpty();
        assertThat(outcome.stopReason()).isEqualTo("AUDIT_FAILED");
        // The message was republished before the audit failed, so a duplicate may exist; the consumer ignores it.
        // The point is that the dead letter itself was not lost.
        awaitDepth(1);
        assertThat(replays.overview().oldest()).extracting(DeadLetter::eventId).containsExactly(event);
        assertThat(count("SELECT COUNT(*) FROM notification_dead_letter_replays")).isZero();
    }

    @Test
    void anEmptyDeadLetterQueueReplaysNothing() {
        DeadLetterReplayService.Outcome outcome = replays.replay(admin, 5);

        assertThat(outcome.replayed()).isEmpty();
        assertThat(outcome.stopReason()).isNull();
        assertThat(replays.overview().depth()).isZero();
    }

    // ---- helpers ----

    /** Puts a message straight into the dead-letter queue, as the broker would after a rejection. */
    private void deadLetter(UUID eventId, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        if (eventId != null) {
            properties.setMessageId(eventId.toString());
        }
        properties.setHeader("x-first-death-reason", "rejected");
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .andProperties(properties).build();
        rabbitTemplate.send("", DEAD_LETTER, message);
    }

    private static String eventJson(UUID eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"data\":{\"parcelId\":\"" + UUID.randomUUID()
                + "\",\"trackingNumber\":\"SD7K3M9Q2XWB\",\"newStatus\":\"DELIVERED\",\"source\":\"CUSTOMER\","
                + "\"ownerCustomerId\":\"" + UUID.randomUUID() + "\"}}";
    }

    private void awaitDepth(long expected) throws InterruptedException {
        awaitTrue(() -> replays.overview().depth() == expected);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static void awaitTrue(Supplier<Boolean> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.get()).as("condition within 20 seconds").isTrue();
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, full_name, enabled, created_at) VALUES (?, ?, ?, TRUE, ?)",
                id, "admin-" + id + "@example.com", "Test admin", Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, "ADMIN");
        return id;
    }
}
