package com.almaraama.identity.adapters.out.persistence;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.domain.EmailDispatch;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data repository of e-mail traces, schema identity. Extends {@link Repository}: no delete method (M2). */
interface SpringDataEmailDispatches extends Repository<EmailDispatch, UUID> {

    /** Number of traces of one person, kind and outcome since a time. */
    long countByPersonIdAndKindAndOutcomeAndCreatedAtAfter(UUID personId, AccountEmailKind kind,
                                                           EmailDispatch.Outcome outcome, Instant since);

    /** Appends a trace. */
    EmailDispatch save(EmailDispatch dispatch);
}
