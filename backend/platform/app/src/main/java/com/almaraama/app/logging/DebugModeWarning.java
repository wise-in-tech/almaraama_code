package com.almaraama.app.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Writes a warning at start-up when the debug mode ({@code ALMARAAMA_LOG_LEVEL} = DEBUG or TRACE) is on outside the
 * dev profile, so that a debug mode forgotten in acceptance or production is seen (ADR-033).
 */
@Component
class DebugModeWarning {

    private static final Logger LOG = LoggerFactory.getLogger(DebugModeWarning.class);

    private final Environment environment;

    /** @param environment reads the log level and the active profiles */
    DebugModeWarning(Environment environment) {
        this.environment = environment;
    }

    /** Checks the level of the "almaraama" log group once the application is ready. */
    @EventListener(ApplicationReadyEvent.class)
    void check() {
        String level = environment.getProperty("logging.level.almaraama", "INFO");
        boolean debug = "DEBUG".equalsIgnoreCase(level) || "TRACE".equalsIgnoreCase(level);
        if (debug && !environment.acceptsProfiles(Profiles.of("dev"))) {
            LOG.warn("Debug mode is ON (ALMARAAMA_LOG_LEVEL={}) outside the dev profile: switch it off after testing",
                    level);
        }
    }
}
