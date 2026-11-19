package com.swiftdrop.delivery.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publisher-owned exchange; the outbox relay is implemented separately (ADR 0004). */
@Configuration(proxyBeanMethods = false)
public class DeliveryMessagingConfiguration {

    @Bean
    TopicExchange parcelEventsExchange() {
        return new TopicExchange("swiftdrop.events", true, false);
    }
}
