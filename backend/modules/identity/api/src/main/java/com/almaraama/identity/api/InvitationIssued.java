package com.almaraama.identity.api;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import java.util.Optional;

/**
 * Result of {@link IdentityApi#invitePerson}. The link itself is never returned: it is sent to the person by e-mail
 * (ADR-031). {@code linkExpiresAt} is present when a link was requested.
 */
public record InvitationIssued(PersonId personId, Optional<Instant> linkExpiresAt) {

    /** The person already has an account: no link was sent, access and roles apply at once. */
    public static InvitationIssued existingAccount(PersonId personId) {
        return new InvitationIssued(personId, Optional.empty());
    }

    /** Whether no link was sent because the account already existed. */
    public boolean existing() {
        return linkExpiresAt.isEmpty();
    }
}
