package com.almaraama.audit.adapters.in.events;

import com.almaraama.audit.domain.AuditEntry;
import com.almaraama.audit.domain.AuditJournal;
import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.api.events.AccountEmailRequestedV1;
import com.almaraama.identity.api.events.AccountEmailThrottledV1;
import com.almaraama.identity.api.events.CredentialLockedV1;
import com.almaraama.identity.api.events.EmailVerifiedV1;
import com.almaraama.identity.api.events.InvitationIssuedV1;
import com.almaraama.identity.api.events.LoginFailedV1;
import com.almaraama.identity.api.events.LoginSucceededV1;
import com.almaraama.identity.api.events.PasswordChangedV1;
import com.almaraama.identity.api.events.PersonActivatedV1;
import com.almaraama.identity.api.events.RefreshTokenReuseDetectedV1;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records the identity module's security events in the journal. Runs after the identity transaction has
 * committed, in its own transaction; a failure is retried from the outbox (CF-6).
 */
@Component
class IdentityEventsAuditor {

    private static final String PERSON = "PERSON";

    private final AuditJournal journal;

    /** @param journal the append-only journal */
    IdentityEventsAuditor(AuditJournal journal) {
        this.journal = journal;
    }

    /** Journals {@code INVITATION_ISSUED}: an account created by a manager, or a new "set my password" link (D4). */
    @ApplicationModuleListener
    void on(InvitationIssuedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.issuedBy(), "INVITATION_ISSUED", PERSON,
                event.personId().value(), Map.of()));
    }

    /**
     * Journals the password links asked for (ADR-030): {@code PASSWORD_RESET_REQUESTED} when the person asked on the
     * public form, {@code PASSWORD_LINK_SENT_BY_MANAGER} when a manager sent it. Other account e-mails are traced by the
     * identity module itself ({@code email_dispatch}).
     */
    @ApplicationModuleListener
    void on(AccountEmailRequestedV1 event) {
        boolean byPerson = event.requestedBy().type() == ActorType.PERSON;
        boolean passwordLink = event.kind() == AccountEmailKind.PASSWORD_RESET
                || event.kind() == AccountEmailKind.SET_PASSWORD;
        if (!passwordLink) {
            return;
        }
        if (byPerson) {
            journal.append(AuditEntry.of(event.occurredAt(), event.requestedBy(), "PASSWORD_LINK_SENT_BY_MANAGER",
                    PERSON, event.personId().value(), Map.of("kind", event.kind().name())));
        } else if (event.kind() == AccountEmailKind.PASSWORD_RESET) {
            journal.append(AuditEntry.of(event.occurredAt(), Actor.platform(), "PASSWORD_RESET_REQUESTED", PERSON,
                    event.personId().value(), Map.of()));
        }
    }

    /** Journals {@code EMAIL_SEND_THROTTLED}: the hourly limit held an e-mail back (possible abuse, ADR-031). */
    @ApplicationModuleListener
    void on(AccountEmailThrottledV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.platform(), "EMAIL_SEND_THROTTLED", PERSON,
                event.personId().value(), Map.of("kind", event.kind().name())));
    }

    /** Journals {@code EMAIL_VERIFIED} (D1). */
    @ApplicationModuleListener
    void on(EmailVerifiedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.person(event.personId()), "EMAIL_VERIFIED", PERSON,
                event.personId().value(), Map.of()));
    }

    /** Journals {@code PASSWORD_SET} (link of D4) or {@code PASSWORD_RESET_COMPLETED} (forgotten password, D5). */
    @ApplicationModuleListener
    void on(PasswordChangedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.person(event.personId()),
                event.reset() ? "PASSWORD_RESET_COMPLETED" : "PASSWORD_SET", PERSON, event.personId().value(),
                Map.of()));
    }

    /** Journals {@code PERSON_ACTIVATED}: password chosen with a link, or e-mail verified after self-registration. */
    @ApplicationModuleListener
    void on(PersonActivatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.person(event.personId()), "PERSON_ACTIVATED",
                PERSON, event.personId().value(), Map.of()));
    }

    /** Journals {@code LOGIN_SUCCEEDED} with the device. */
    @ApplicationModuleListener
    void on(LoginSucceededV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.person(event.personId()), "LOGIN_SUCCEEDED",
                PERSON, event.personId().value(), Map.of("deviceId", event.deviceId().toString())));
    }

    /** Journals {@code LOGIN_FAILED}, noting whether the account exists (for brute-force alerts). */
    @ApplicationModuleListener
    void on(LoginFailedV1 event) {
        Actor actor = event.personId().map(Actor::person).orElseGet(Actor::platform);
        journal.append(AuditEntry.of(event.occurredAt(), actor, "LOGIN_FAILED", PERSON,
                event.personId().map(id -> id.value()).orElse(null),
                Map.of("knownAccount", String.valueOf(event.personId().isPresent()))));
    }

    /** Journals {@code CREDENTIAL_LOCKED} with the lock's end. */
    @ApplicationModuleListener
    void on(CredentialLockedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.platform(), "CREDENTIAL_LOCKED", PERSON,
                event.personId().value(), Map.of(
                        "failedAttempts", String.valueOf(event.failedAttempts()),
                        "lockedUntil", event.lockedUntil().toString())));
    }

    /** Journals {@code REFRESH_TOKEN_REUSE_DETECTED}: a probable stolen session. */
    @ApplicationModuleListener
    void on(RefreshTokenReuseDetectedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.platform(), "REFRESH_TOKEN_REUSE_DETECTED", PERSON,
                event.personId().value(), Map.of("deviceId", event.deviceId().toString())));
    }
}
