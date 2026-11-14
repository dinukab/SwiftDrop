package com.swiftdrop.delivery.infrastructure.messaging;

import java.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "swiftdrop.messaging.processing-enabled", havingValue = "true")
public class OutboxRelayConfiguration {
    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, PlatformTransactionManager manager, RabbitTemplate rabbit,
            MeterRegistry meters, @Value("${swiftdrop.messaging.relay-batch-size:100}") int batch,
            @Value("${swiftdrop.messaging.confirm-timeout-seconds:5}") int confirmSeconds,
            @Value("${swiftdrop.messaging.relay-poll-ms:1000}") long pollMs) {
        org.springframework.util.Assert.isTrue(pollMs >= 500 && pollMs <= 10000,
                "Relay polling interval must be between 500 and 10000 milliseconds");
        return new OutboxRelay(jdbc, new TransactionTemplate(manager), rabbit, meters,
                Clock.systemUTC(), batch, confirmSeconds);
    }
}
