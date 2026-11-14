package com.swiftdrop.merchant.infrastructure.webhook;

import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import io.micrometer.core.instrument.MeterRegistry;
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
@ConditionalOnProperty(name = "swiftdrop.webhooks.enabled", havingValue = "true")
class WebhookConfiguration {
    @Bean WebhookSecrets webhookSecrets(
            @Value("${swiftdrop.webhooks.active-key-id:}") String active,
            @Value("${swiftdrop.webhooks.keys:}") String keys) {
        return new WebhookSecrets(active, keys);
    }

    @Bean WebhookDestination webhookDestination(
            @Value("${swiftdrop.webhooks.allow-local:false}") boolean local,
            @Value("${swiftdrop.webhooks.denied-hosts:}") String denied) {
        if (local) org.slf4j.LoggerFactory.getLogger(WebhookConfiguration.class)
                .warn("Local webhook destinations enabled; do not use this setting in production");
        return new WebhookDestination(local, Arrays.stream(denied.split(",")).map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> !s.isBlank()).collect(Collectors.toSet()));
    }

    @Bean WebhookStore webhookStore(JdbcTemplate jdbc, PlatformTransactionManager manager,
            WebhookSecrets secrets, WebhookDestination destination) {
        return new WebhookStore(jdbc, new TransactionTemplate(manager), secrets, destination);
    }

    @Bean WebhookHttpSender webhookHttpSender(WebhookDestination destination) {
        return new WebhookHttpSender(destination, Clock.systemUTC());
    }

    @Bean WebhookWorker webhookWorker(JdbcTemplate jdbc, PlatformTransactionManager manager,
            WebhookSecrets secrets, WebhookHttpSender sender, MeterRegistry meters) {
        return new WebhookWorker(jdbc, new TransactionTemplate(manager), secrets, sender, Clock.systemUTC(), meters);
    }
}
