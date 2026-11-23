package com.almaraama.app.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on scheduled jobs (outbox resubmission). Off when {@code almaraama.scheduling.enabled=false}, which the tests
 * set so that no background job runs during a test.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "almaraama.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfiguration {
}
