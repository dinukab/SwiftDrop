package com.swiftdrop.config;

import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.messaging.processing-enabled", havingValue = "true")
public class ReliableMessagingConfiguration {
    @Bean
    SimpleRabbitListenerContainerFactory reliableEventListenerFactory(ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        factory.setErrorHandler(error -> {
            LoggerFactory.getLogger(ReliableMessagingConfiguration.class)
                    .error("Status event rejected; inspect the consumer dead-letter queue");
            throw new AmqpRejectAndDontRequeueException("Status event rejected");
        });
        return factory;
    }
}
