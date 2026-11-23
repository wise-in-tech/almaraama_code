package com.almaraama.app.events;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries the event listeners that failed (CF-6, ADR-031): every 5 minutes, the outbox entries left incomplete for
 * more than 5 minutes (for example an e-mail refused by the relay) are submitted again. Restarts also resubmit them
 * ({@code republish-outstanding-events-on-restart}).
 */
@Component
class OutboxResubmission {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxResubmission.class);

    private final IncompleteEventPublications incomplete;

    /** @param incomplete Spring Modulith's access to the incomplete outbox entries */
    OutboxResubmission(IncompleteEventPublications incomplete) {
        this.incomplete = incomplete;
    }

    /** Resubmits the entries older than 5 minutes. */
    @Scheduled(fixedDelayString = "${almaraama.events.resubmit-every:5m}",
            initialDelayString = "${almaraama.events.resubmit-every:5m}")
    void resubmit() {
        LOG.debug("Resubmitting incomplete event publications older than 5 minutes");
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));
    }
}
