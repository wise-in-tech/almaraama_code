package com.almaraama.identity.domain;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/** Out port: traces of the account e-mails (ADR-031). */
public interface EmailDispatchRepository {

    /** E-mails of this kind actually sent to the person since the given time (sending limit). */
    long countSentSince(PersonId personId, AccountEmailKind kind, Instant since);

    /** Appends a trace. */
    EmailDispatch save(EmailDispatch dispatch);
}
