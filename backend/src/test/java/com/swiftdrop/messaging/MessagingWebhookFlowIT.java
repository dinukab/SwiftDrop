package com.swiftdrop.messaging;

import static org.assertj.core.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import com.swiftdrop.delivery.api.*;
import com.swiftdrop.delivery.infrastructure.messaging.OutboxRelay;
import com.swiftdrop.merchant.infrastructure.messaging.MerchantStatusConsumer;
import com.swiftdrop.merchant.infrastructure.messaging.MerchantWebhookEffect;
import com.swiftdrop.merchant.infrastructure.webhook.*;
import com.swiftdrop.notification.infrastructure.messaging.NotificationStatusConsumer;

/** Real outbox -> RabbitMQ -> ordered effects -> signed HTTP; no development data or services. */
@Testcontainers
class MessagingWebhookFlowIT {
    static final String PASSWORD = UUID.randomUUID().toString();
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
    @Container static GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:4.3-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "flow-test").withEnv("RABBITMQ_DEFAULT_PASS", PASSWORD)
            .withExposedPorts(5672).waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));
    final JsonMapper mapper = JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    @Test void committedOutboxRetriesSignedWebhookAndCustomerNotificationIsIsolated() throws Exception {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        var jdbc = new JdbcTemplate(source);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        var meters = new SimpleMeterRegistry();
        UUID user = UUID.randomUUID(), merchant = UUID.randomUUID(), parcel = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,full_name,created_at) VALUES (?,?,'Flow test',now())", user, user + "@example.test");
        jdbc.update("""
                INSERT INTO merchants(id,user_id,business_name,registration_number,contact_email,contact_phone,status,created_at,updated_at)
                VALUES (?,?,'Flow merchant',?,?,'0000000000','APPROVED',now(),now())
                """, merchant, user, merchant.toString(), user + "@example.test");
        var secrets = new WebhookSecrets("test", "test:" + Base64.getEncoder().encodeToString(new byte[32]));
        var destination = new WebhookDestination(true, Set.of());
        var store = new WebhookStore(jdbc, tx, secrets, destination);
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new AtomicInteger();
        var receiverSecret = new AtomicReference<byte[]>();
        var signaturesValid = new java.util.concurrent.atomic.AtomicBoolean(true);
        var bodies = new java.util.concurrent.CopyOnWriteArrayList<String>();
        http.createContext("/webhooks", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String timestamp = exchange.getRequestHeaders().getFirst("X-SwiftDrop-Timestamp");
            try {
                var mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(receiverSecret.get(), "HmacSHA256"));
                String expected = "v1=" + java.util.HexFormat.of().formatHex(mac.doFinal(
                        (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
                signaturesValid.compareAndSet(true, expected.equals(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature")));
            } catch (Exception ex) { signaturesValid.set(false); }
            bodies.add(body);
            exchange.sendResponseHeaders(requests.incrementAndGet() <= 2 ? 503 : 204, -1);
            exchange.close();
        });
        http.start();
        try {
            var endpoint = store.create(user, "http://127.0.0.1:" + http.getAddress().getPort() + "/webhooks");
            receiverSecret.set(Base64.getDecoder().decode(endpoint.secret()));
            var event = event(parcel, merchant, null, 1, ParcelStatus.CREATED);
            tx.executeWithoutResult(status -> {
                jdbc.update("""
                        INSERT INTO parcels(id,tracking_number,source,merchant_id,merchant_order_reference,
                            pickup_contact_name,pickup_phone,pickup_line1,pickup_city,pickup_zone_id,
                            dropoff_contact_name,dropoff_phone,dropoff_line1,dropoff_city,dropoff_zone_id,
                            size,weight_grams,fee_amount,fee_currency,pricing_rule_id,status,last_sequence,created_at,updated_at)
                        VALUES (?,'SD0000000001','MERCHANT',?,'FLOW-1','Test','000','Test','Test',?,
                            'Test','000','Test','Test',?,'SMALL',100,100,'LKR',?,'CREATED',1,now(),now())
                        """, parcel, merchant, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
                jdbc.update("""
                        INSERT INTO parcel_outbox_events(event_id,parcel_id,sequence,event_type,payload,created_at,next_attempt_at)
                        VALUES (?,?,1,?,?::jsonb,?,?)
                        """, event.eventId(), parcel, event.eventType(), mapper.writeValueAsString(event),
                        Timestamp.from(event.occurredAt()), Timestamp.from(event.occurredAt()));
            });
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("webhookEffect", store);
            ParcelEvents history = (id, from, to) -> List.of();
            var merchantConsumer = new MerchantStatusConsumer(jdbc, tx, history, meters, beans.getBeanProvider(MerchantWebhookEffect.class));
            var notificationConsumer = new NotificationStatusConsumer(jdbc, tx, history, meters);
            MessagingTestContext.runner().withPropertyValues(
                    "spring.rabbitmq.host=" + rabbit.getHost(), "spring.rabbitmq.port=" + rabbit.getMappedPort(5672),
                    "spring.rabbitmq.username=flow-test", "spring.rabbitmq.password=" + PASSWORD).run(context -> {
                context.getBean(RabbitAdmin.class).initialize();
                var template = context.getBean(RabbitTemplate.class);
                new OutboxRelay(jdbc, tx, template, meters, Clock.systemUTC(), 10, 5).poll();
                var message = template.receive("merchant.parcel-status-changed", 5000);
                assertThat(message).isNotNull();
                merchantConsumer.receive(message);
                merchantConsumer.receive(message); // redelivery is harmless
                notificationConsumer.receive(template.receive("notification.parcel-status-changed", 5000));
                assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_webhook_deliveries", Integer.class)).isOne();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_messages", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM parcel_outbox_events", Boolean.class)).isTrue();
                var worker = new WebhookWorker(jdbc, tx, secrets, new WebhookHttpSender(destination, Clock.systemUTC()), Clock.systemUTC(), meters);
                for (int attempt = 1; attempt <= 3; attempt++) {
                    jdbc.update("UPDATE merchant_webhook_deliveries SET next_attempt_at=now()-interval '1 second'");
                    worker.poll();
                }
                assertThat(jdbc.queryForObject("SELECT state FROM merchant_webhook_deliveries", String.class)).isEqualTo("SUCCEEDED");
                UUID customerParcel = UUID.randomUUID();
                for (int sequence = 1; sequence <= 2; sequence++) {
                    var customerEvent = event(customerParcel, null, user, sequence,
                            sequence == 1 ? ParcelStatus.CREATED : ParcelStatus.PICKED_UP);
                    template.send("swiftdrop.events", customerEvent.eventType(), message(customerEvent));
                    merchantConsumer.receive(template.receive("merchant.parcel-status-changed", 5000));
                    notificationConsumer.receive(template.receive("notification.parcel-status-changed", 5000));
                }
                assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_messages", Integer.class)).isOne();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant_webhook_deliveries", Integer.class)).isOne();
                assertThat(jdbc.queryForList("SELECT http_status FROM merchant_webhook_attempts ORDER BY attempt", Integer.class))
                        .containsExactly(503, 503, 204);
            });
            assertThat(requests.get()).isEqualTo(3);
            assertThat(signaturesValid.get()).isTrue();
            assertThat(bodies).hasSize(3).allMatch(body -> body.equals(bodies.getFirst()));
        } finally { http.stop(0); }
    }

    ParcelStatusChanged event(UUID parcel, UUID merchant, UUID customer, int sequence, ParcelStatus status) {
        return ParcelStatusChanged.of(UUID.randomUUID(), Instant.now(), new ParcelStatusChangedData(parcel,
                "SD0000000001", sequence == 1 ? null : ParcelStatus.CREATED, status, sequence,
                merchant == null ? ParcelSource.CUSTOMER : ParcelSource.MERCHANT, customer, merchant, null, null));
    }

    Message message(ParcelStatusChanged event) {
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId(event.eventId().toString());
        properties.setType(event.eventType());
        return new Message(mapper.writeValueAsBytes(event), properties);
    }
}
