package com.swiftdrop.notification.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.swiftdrop.delivery.api.ParcelEvents;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "swiftdrop.messaging.processing-enabled", havingValue = "true")
public class NotificationProcessingConfiguration {
    @Bean
    NotificationStatusConsumer notificationStatusConsumer(JdbcTemplate jdbc, PlatformTransactionManager manager,
            ParcelEvents history, MeterRegistry meters) {
        return new NotificationStatusConsumer(jdbc, new TransactionTemplate(manager), history, meters);
    }
}
