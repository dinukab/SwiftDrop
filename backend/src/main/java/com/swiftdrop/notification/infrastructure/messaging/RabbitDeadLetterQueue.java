package com.swiftdrop.notification.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;

import com.swiftdrop.notification.application.DeadLetter;
import com.swiftdrop.notification.application.DeadLetterQueue;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The broker side of dead-letter replay (ADR 0004 section 6). Messages are read with {@code basicGet} and manual
 * acknowledgement, republished to the source queue through the default exchange with publisher confirms and the
 * mandatory flag, and acknowledged only after the confirm and the audit record. Any failure puts the message back
 * ({@code basicNack} with requeue), so a dead letter is never lost: the worst case is a duplicate delivery, which the
 * consumer ignores.
 */
@Component
@Lazy
class RabbitDeadLetterQueue implements DeadLetterQueue {

    static final String SOURCE_QUEUE = ParcelStatusNotificationConsumer.QUEUE;
    static final String DEAD_LETTER_QUEUE = SOURCE_QUEUE + ".dlq";
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final RabbitTemplate rabbit;

    RabbitDeadLetterQueue(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public String sourceQueue() {
        return SOURCE_QUEUE;
    }

    @Override
    public long depth() {
        Integer count = rabbit.execute(channel -> channel.queueDeclarePassive(DEAD_LETTER_QUEUE).getMessageCount());
        return count == null ? 0 : count;
    }

    @Override
    public List<DeadLetter> peek(int max) {
        return rabbit.invoke(operations -> {
            List<DeadLetter> letters = new ArrayList<>();
            long lastTag = -1;
            for (int i = 0; i < max; i++) {
                GetResponse response = operations.execute(channel -> channel.basicGet(DEAD_LETTER_QUEUE, false));
                if (response == null) {
                    break;
                }
                lastTag = response.getEnvelope().getDeliveryTag();
                letters.add(summarize(response.getBody(), response.getProps()));
            }
            if (lastTag >= 0) {
                long tag = lastTag;
                // Put every message we only looked at back
                operations.execute(channel -> {
                    channel.basicNack(tag, true, true);
                    return null;
                });
            }
            return letters;
        });
    }

    @Override
    public Result replayOldest(int max, Consumer<DeadLetter> beforeAcknowledge) {
        List<DeadLetter> replayed = new ArrayList<>();
        String stopReason = null;
        try {
            // A message that fails again returns to the back of the dead-letter queue. Remember what this request
            // replayed so it is never picked up a second time by the same request.
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < max && stopReason == null; i++) {
                Step step = rabbit.invoke(operations -> replayOne(operations, beforeAcknowledge, seen));
                if (step.letter() == null) {
                    break; // the dead-letter queue is empty
                }
                if (step.stopReason() == null) {
                    replayed.add(step.letter());
                } else {
                    stopReason = step.stopReason();
                }
            }
        } catch (AmqpException e) {
            stopReason = "BROKER_UNAVAILABLE";
        }
        return new Result(List.copyOf(replayed), stopReason);
    }

    private Step replayOne(RabbitOperations operations, Consumer<DeadLetter> beforeAcknowledge, Set<String> seen) {
        GetResponse response = operations.execute(channel -> channel.basicGet(DEAD_LETTER_QUEUE, false));
        if (response == null) {
            return new Step(null, null);
        }
        long tag = response.getEnvelope().getDeliveryTag();
        if (!seen.add(identity(response))) {
            // It was replayed by this request and came back: leave it for the next request
            operations.execute(channel -> {
                channel.basicNack(tag, false, true);
                return null;
            });
            return new Step(null, null);
        }
        DeadLetter letter = summarize(response.getBody(), response.getProps());

        String failure = republish(operations, response);
        if (failure == null) {
            try {
                beforeAcknowledge.accept(letter);
            } catch (RuntimeException e) {
                failure = "AUDIT_FAILED";
            }
        }
        if (failure != null) {
            operations.execute(channel -> {
                channel.basicNack(tag, false, true); // it stays in the dead-letter queue
                return null;
            });
            return new Step(letter, failure);
        }
        operations.execute(channel -> {
            channel.basicAck(tag, false);
            return null;
        });
        return new Step(letter, null);
    }

    /** The message ID, or for a message without one a fingerprint of its body. */
    private static String identity(GetResponse response) {
        String messageId = response.getProps() == null ? null : response.getProps().getMessageId();
        return messageId != null ? "id:" + messageId : "body:" + UUID.nameUUIDFromBytes(response.getBody());
    }

    /** Publishes the original body to the source queue; returns {@code null} once the broker confirmed it. */
    private String republish(RabbitOperations operations, GetResponse response) {
        AMQP.BasicProperties props = response.getProps();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(props.getContentType() == null
                ? MessageProperties.CONTENT_TYPE_JSON : props.getContentType());
        properties.setMessageId(props.getMessageId());
        properties.setType(props.getType());
        Message message = MessageBuilder.withBody(response.getBody()).andProperties(properties).build();
        // A malformed dead letter may carry no message ID; the confirm still needs a correlation ID
        CorrelationData correlation = new CorrelationData(
                props.getMessageId() != null ? props.getMessageId() : UUID.randomUUID().toString());
        try {
            // Default exchange: the routing key is the queue name, so it reaches the source queue only
            operations.send("", SOURCE_QUEUE, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (correlation.getReturned() != null) {
                return "NOT_ROUTED";
            }
            return confirm.ack() ? null : "NOT_CONFIRMED";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "BROKER_UNAVAILABLE";
        } catch (ExecutionException | TimeoutException | AmqpException e) {
            return "BROKER_UNAVAILABLE";
        }
    }

    /** Reads the event ID and status from the body and the reason from the broker headers; never keeps the body. */
    static DeadLetter summarize(byte[] body, AMQP.BasicProperties props) {
        Map<String, Object> headers = props == null || props.getHeaders() == null ? Map.of() : props.getHeaders();
        UUID eventId = null;
        String status = null;
        try {
            ParcelStatusNotificationConsumer.Envelope event = MAPPER.readValue(
                    new String(body, StandardCharsets.UTF_8), ParcelStatusNotificationConsumer.Envelope.class);
            if (event != null) {
                eventId = event.eventId();
                status = event.data() == null ? null : event.data().newStatus();
            }
        } catch (RuntimeException e) {
            // Not parseable: still listed and still replayable, without an event ID
        }

        String reason = headers.get("x-first-death-reason") == null
                ? null : String.valueOf(headers.get("x-first-death-reason"));
        long count = 0;
        Instant first = null;
        if (headers.get("x-death") instanceof Collection<?> deaths) {
            for (Object death : deaths) {
                if (death instanceof Map<?, ?> entry) {
                    if (entry.get("count") instanceof Number n) {
                        count += n.longValue();
                    }
                    if (reason == null && entry.get("reason") != null) {
                        reason = String.valueOf(entry.get("reason"));
                    }
                    if (first == null && entry.get("time") instanceof Date time) {
                        first = time.toInstant();
                    }
                }
            }
        }
        return new DeadLetter(eventId, status, reason, count, first);
    }

    private record Step(DeadLetter letter, String stopReason) {
    }
}
