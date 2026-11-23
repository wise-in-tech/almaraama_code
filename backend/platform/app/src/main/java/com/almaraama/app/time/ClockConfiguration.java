package com.almaraama.app.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The single clock of the platform, in UTC. Code never calls {@code now()} directly, so tests control time. */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    /** The single source of time of the application, in UTC; tests replace it to control dates. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
