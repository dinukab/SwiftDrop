package com.swiftdrop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on scheduled tasks. Only the outbox relay and the webhook sender schedule anything, and both
 * are switched on by their own properties, so nothing runs in a context that does not enable them.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {
}
