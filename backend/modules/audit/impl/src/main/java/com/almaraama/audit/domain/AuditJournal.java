package com.almaraama.audit.domain;

/** Out port: the append-only journal. There is deliberately no method to change or remove an entry. */
public interface AuditJournal {

    /** Adds an entry to the journal, after the action it records has been committed. */
    void append(AuditEntry entry);
}
