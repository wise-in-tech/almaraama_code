package com.almaraama.maintenance.adapters.in.scheduling;

import com.almaraama.maintenance.application.TicketService;
import com.almaraama.workflow.api.DueTransition;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * H5 option, criterion 19 (ADR-036): every hour, closes the "resolved" tickets of the clients that enabled automatic
 * closing, once their number of days has passed. Locked in the database (ShedLock) so that one instance runs it; each
 * ticket in its own transaction, through the same use case as a person, as the {@code SYSTEM} actor.
 */
@Component
public class TicketAutoClose {

    private static final Logger LOG = LoggerFactory.getLogger(TicketAutoClose.class);
    private static final int BATCH = 500;

    private final TicketService tickets;

    /** @param tickets the ticket use cases */
    TicketAutoClose(TicketService tickets) {
        this.tickets = tickets;
    }

    /** The scheduled run; disabled with {@code almaraama.scheduling.enabled=false} (tests). */
    @Scheduled(cron = "${almaraama.maintenance.auto-close-cron:0 7 * * * *}")
    @SchedulerLock(name = "ticket-auto-close", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void scheduled() {
        run();
    }

    /** Closes every due ticket; a failure on one ticket does not stop the others. Returns how many were closed. */
    public int run() {
        List<DueTransition> due = tickets.dueAutomaticTransitions(BATCH);
        int closed = 0;
        for (DueTransition transition : due) {
            try {
                tickets.takeAutomatic(transition);
                closed++;
            } catch (RuntimeException e) {
                LOG.warn("Automatic closing of ticket {} failed: {}", transition.subjectId(),
                        e.getClass().getSimpleName());
            }
        }
        if (closed > 0) {
            LOG.info("{} tickets closed automatically", closed);
        }
        return closed;
    }
}
