package com.swiftdrop.notification.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import com.swiftdrop.delivery.api.ParcelStatusChanged;

/** Consumer-owned queues and bindings, including isolated dead letters (ADR 0004 section 5). */
@Configuration(proxyBeanMethods = false)
public class NotificationMessagingConfiguration {

    private static final String QUEUE = "notification.parcel-status-changed";
    private static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dead-letter";

    @Bean
    Declarables notificationParcelStatusTopology(
            @Value("${swiftdrop.messaging.delivery-limit:5}") int deliveryLimit,
            @Value("${swiftdrop.messaging.queue-max-length:100000}") long maxLength) {
        Assert.isTrue(deliveryLimit >= 2 && deliveryLimit <= 20,
                "Messaging delivery limit must be between 2 and 20");
        Assert.isTrue(maxLength > 0, "Messaging queue maximum length must be positive");

        Queue source = QueueBuilder.durable(QUEUE)
                .quorum()
                .deliveryLimit(deliveryLimit)
                .maxLength(maxLength)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QUEUE)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .build();
        // No TTL, maximum length or dead-letter exchange: dead letters must not expire or cycle.
        Queue deadLetters = QueueBuilder.durable(QUEUE + ".dlq").quorum().build();
        DirectExchange exchange = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);

        return new Declarables(source, deadLetters, exchange,
                new Binding(QUEUE, Binding.DestinationType.QUEUE, "swiftdrop.events",
                        ParcelStatusChanged.EVENT_TYPE, null),
                new Binding(deadLetters.getName(), Binding.DestinationType.QUEUE,
                        DEAD_LETTER_EXCHANGE, QUEUE, null));
    }
}
