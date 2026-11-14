package com.swiftdrop.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.delivery.application.ParcelEventSerializer;

/** Isolated broker only; uses the existing Testcontainers core, not development containers or .env. */
@Testcontainers
class RabbitMessagingIT {

    private static final String MERCHANT = "merchant.parcel-status-changed";
    private static final String NOTIFICATION = "notification.parcel-status-changed";
    private static final String USER = "messaging-test";
    private static final String PASSWORD = UUID.randomUUID().toString();

    @Container
    static GenericContainer<?> rabbit = new GenericContainer<>(
            DockerImageName.parse("rabbitmq:4.3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", USER)
            .withEnv("RABBITMQ_DEFAULT_PASS", PASSWORD)
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    @BeforeAll
    static void verifiesRequiredFeatureFlag() throws Exception {
        var result = rabbit.execInContainer("rabbitmqctl", "eval",
                "rabbit_feature_flags:is_enabled(stream_queue).");
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout().trim()).isEqualTo("true");
    }

    @Test
    void sampleEventReachesBothQueuesWithPersistentPropertiesAndPositiveConfirm() {
        withBroker((template, admin) -> {
            Message original = sampleMessage();
            publish(template, original, "parcel.status-changed");
            for (String queue : List.of(MERCHANT, NOTIFICATION)) {
                Message received = template.receive(queue, 5000);
                assertThat(received).isNotNull();
                assertThat(received.getBody()).isEqualTo(original.getBody());
                assertThat(received.getMessageProperties().getMessageId())
                        .isEqualTo(original.getMessageProperties().getMessageId());
                assertThat(received.getMessageProperties().getContentType()).isEqualTo("application/json");
                assertThat(received.getMessageProperties().getType()).isEqualTo("parcel.status-changed");
                assertThat(received.getMessageProperties().getReceivedDeliveryMode())
                        .isEqualTo(MessageDeliveryMode.PERSISTENT);
            }
        });
    }

    @Test
    void unroutableMessageIsReturnedEvenWhenBrokerAcknowledgesPublish() {
        withBroker((template, admin) -> {
            CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
            template.send("swiftdrop.events", "parcel.unknown", sampleMessage(), correlation);
            assertThat(correlation.getFuture().get(5, TimeUnit.SECONDS).ack()).isTrue();
            assertThat(correlation.getReturned()).isNotNull();
            assertThat(correlation.getReturned().getReplyCode()).isEqualTo(312);
            assertThat(template.receive(MERCHANT)).isNull();
            assertThat(template.receive(NOTIFICATION)).isNull();
        });
    }

    @Test
    void rejectionGoesOnlyToOwningDeadLetterQueueWithDeathHeaders() {
        withBroker((template, admin) -> {
            publish(template, sampleMessage(), "parcel.status-changed");
            rejectMerchant(template);
            Message deadLetter = template.receive(MERCHANT + ".dlq", 10000);
            assertThat(deadLetter).isNotNull();
            assertThat(deadLetter.getMessageProperties().getHeaders()).containsKey("x-death");
            assertThat(deadLetter.getMessageProperties().getHeaders().get("x-first-death-queue").toString())
                    .isEqualTo(MERCHANT);
            assertThat(template.receive(NOTIFICATION, 5000)).isNotNull();
            assertThat(template.receive(NOTIFICATION + ".dlq")).isNull();
        });
    }

    @Test
    void deadLetterWaitsUntilMissingTargetQueueIsRestored() {
        withBroker((template, admin) -> {
            admin.deleteQueue(MERCHANT + ".dlq");
            try {
                publish(template, sampleMessage(), "parcel.status-changed");
                rejectMerchant(template);
                // The source retains its unconfirmed dead letter until a target exists.
                admin.declareQueue(QueueBuilder.durable(MERCHANT + ".dlq").quorum().build());
                admin.declareBinding(new org.springframework.amqp.core.Binding(MERCHANT + ".dlq",
                        org.springframework.amqp.core.Binding.DestinationType.QUEUE,
                        "swiftdrop.dead-letter", MERCHANT, null));
                assertThat(template.receive(MERCHANT + ".dlq", 30000)).isNotNull();
            } finally {
                admin.initialize();
            }
        });
    }

    private void withBroker(BrokerCheck check) {
        MessagingTestContext.runner().withPropertyValues(
                "spring.rabbitmq.host=" + rabbit.getHost(),
                "spring.rabbitmq.port=" + rabbit.getMappedPort(5672),
                "spring.rabbitmq.username=" + USER,
                "spring.rabbitmq.password=" + PASSWORD).run(context -> {
                    assertThat(context).hasNotFailed();
                    RabbitAdmin admin = context.getBean(RabbitAdmin.class);
                    admin.initialize(); // Declares the real production beans; broker rejects invalid arguments.
                    for (String queue : List.of(MERCHANT, NOTIFICATION,
                            MERCHANT + ".dlq", NOTIFICATION + ".dlq")) {
                        admin.purgeQueue(queue, false);
                    }
                    check.run(context.getBean(RabbitTemplate.class), admin);
                });
    }

    private void publish(RabbitTemplate template, Message message, String routingKey) throws Exception {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        template.send("swiftdrop.events", routingKey, message, correlation);
        assertThat(correlation.getFuture().get(5, TimeUnit.SECONDS).ack()).isTrue();
        assertThat(correlation.getReturned()).isNull();
    }

    private void rejectMerchant(RabbitTemplate template) {
        template.execute(channel -> {
            var delivery = channel.basicGet(MERCHANT, false);
            assertThat(delivery).isNotNull();
            channel.basicReject(delivery.getEnvelope().getDeliveryTag(), false);
            return null;
        });
    }

    private Message sampleMessage() {
        ParcelStatusChanged event = ParcelStatusChanged.of(UUID.randomUUID(), Instant.now(),
                new ParcelStatusChangedData(UUID.randomUUID(), "SD-DEMO-001", null,
                        ParcelStatus.CREATED, 1, ParcelSource.MERCHANT, null,
                        UUID.randomUUID(), "ORD-DEMO-001", null));
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.eventId().toString());
        properties.setType(event.eventType());
        properties.setTimestamp(Date.from(event.occurredAt()));
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return new Message(new ParcelEventSerializer().toJson(event).getBytes(StandardCharsets.UTF_8),
                properties);
    }

    @FunctionalInterface
    private interface BrokerCheck {
        void run(RabbitTemplate template, RabbitAdmin admin) throws Exception;
    }
}
