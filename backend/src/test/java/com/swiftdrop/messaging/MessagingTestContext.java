package com.swiftdrop.messaging;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.ResourcePropertySource;

import com.swiftdrop.delivery.infrastructure.messaging.DeliveryMessagingConfiguration;
import com.swiftdrop.merchant.infrastructure.messaging.MerchantMessagingConfiguration;
import com.swiftdrop.notification.infrastructure.messaging.NotificationMessagingConfiguration;

/** Loads the actual application properties without importing .env or starting the full backend. */
final class MessagingTestContext {

    private MessagingTestContext() {
    }

    static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    try {
                        context.getEnvironment().getPropertySources().addLast(
                                new ResourcePropertySource("classpath:application.properties"));
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                })
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withUserConfiguration(DeliveryMessagingConfiguration.class,
                        MerchantMessagingConfiguration.class, NotificationMessagingConfiguration.class)
                .withPropertyValues("spring.rabbitmq.host=localhost", "spring.rabbitmq.port=5672",
                        "spring.rabbitmq.username=unused", "spring.rabbitmq.password=unused");
    }
}
