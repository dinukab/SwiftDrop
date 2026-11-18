package com.swiftdrop.delivery.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.domain.OutboxEvent;
import com.swiftdrop.delivery.domain.OutboxEventRepository;

/**
 * Transactional outbox relay (ADR 0004 section 3, 4.1): publishes committed {@code parcel.status-changed}
 * events to exchange {@code swiftdrop.events} and marks each one published only after the broker has
 * confirmed it.
 *
 * <p>Events leave strictly in the order they were written. The first event that fails stops the run
 * and is retried later, so no later event overtakes it. Run one relay per deployment; several relays
 * sharing the outbox would break that order. Off unless {@code swiftdrop.messaging.relay.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "swiftdrop.messaging.relay.enabled", havingValue = "true")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    static final String EXCHANGE = "swiftdrop.events";
    static final String ROUTING_KEY = "parcel.status-changed";
    static final int BATCH_SIZE = 50;
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    @Autowired
    public OutboxRelay(OutboxEventRepository outbox, RabbitTemplate rabbit) {
        this(outbox, rabbit, Clock.systemUTC());
    }

    OutboxRelay(OutboxEventRepository outbox, RabbitTemplate rabbit, Clock clock) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.clock = clock;
    }

    /**
     * Publishes one batch of due events, in order. Returns how many were confirmed.
     */
    @Scheduled(fixedDelayString = "${swiftdrop.messaging.relay.interval-ms:1000}")
    @Transactional
    public int relayBatch() {
        Instant now = clock.instant();
        int published = 0;
        List<OutboxEvent> batch = outbox.lockUnpublished(BATCH_SIZE);
        for (OutboxEvent event : batch) {
            if (event.getNextAttemptAt().isAfter(now)) {
                break; // an earlier event is waiting for a retry: nothing after it may go first
            }
            Outcome outcome = publish(event);
            if (outcome == Outcome.CONFIRMED) {
                event.markPublished(clock.instant());
                published++;
            } else {
                event.markFailed(outcome.category(), clock.instant().plus(backoff(event.getAttempts() + 1)));
                // IDs and codes only: the broker's own message text is never logged
                log.warn("Outbox event {} not published ({}), attempt {}", event.getEventId(), outcome.category(),
                        event.getAttempts());
                break;
            }
        }
        if (published > 0) {
            log.info("Outbox relay published {} event(s)", published);
        }
        return published;
    }

    private Outcome publish(OutboxEvent event) {
        try {
            Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(event.getEventId().toString())
                    .setType(event.getEventType())
                    .build();
            CorrelationData correlation = new CorrelationData(event.getEventId().toString());
            rabbit.send(EXCHANGE, ROUTING_KEY, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (correlation.getReturned() != null) {
                return Outcome.NOT_ROUTED; // mandatory publish with no bound queue
            }
            return confirm.ack() ? Outcome.CONFIRMED : Outcome.NOT_CONFIRMED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.BROKER_UNAVAILABLE;
        } catch (ExecutionException | TimeoutException | AmqpException e) {
            return Outcome.BROKER_UNAVAILABLE;
        }
    }

    /** Retry delay after {@code attempt} failures: 1 min, doubling, capped at one hour. */
    static Duration backoff(int attempt) {
        long minutes = Math.min(60L, 1L << Math.min(attempt, 6));
        return Duration.ofMinutes(Math.max(1L, minutes));
    }

    enum Outcome {
        CONFIRMED(null),
        NOT_ROUTED("NOT_ROUTED"),
        NOT_CONFIRMED("NOT_CONFIRMED"),
        BROKER_UNAVAILABLE("BROKER_UNAVAILABLE");

        private final String category;

        Outcome(String category) {
            this.category = category;
        }

        String category() {
            return category;
        }
    }
}
