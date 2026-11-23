package com.almaraama.audit.adapters.out.persistence;

import com.almaraama.audit.domain.AuditEntry;
import com.almaraama.audit.domain.AuditJournal;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** The journal in schema {@code audit}: insert only. */
@Component
class JpaAuditJournal implements AuditJournal {

    private final AuditEntryRows rows;
    private final Clock clock;

    /**
     * @param rows Spring Data repository of the journal table
     * @param clock time of recording
     */
    JpaAuditJournal(AuditEntryRows rows, Clock clock) {
        this.rows = rows;
        this.clock = clock;
    }

    /** Inserts the entry; there is no way to change or remove one. */
    @Override
    public void append(AuditEntry entry) {
        rows.save(AuditEntryRow.from(entry, clock.instant()));
    }
}
