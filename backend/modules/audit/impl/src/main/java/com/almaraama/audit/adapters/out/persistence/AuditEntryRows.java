package com.almaraama.audit.adapters.out.persistence;

import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data repository of journal entries: insert only, no update or delete method (M2). */
interface AuditEntryRows extends Repository<AuditEntryRow, UUID> {

    /** Inserts a journal line; the table refuses updates. */
    AuditEntryRow save(AuditEntryRow row);
}
