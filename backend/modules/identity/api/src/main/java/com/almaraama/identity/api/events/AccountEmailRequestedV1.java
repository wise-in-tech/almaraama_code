package com.almaraama.identity.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/**
 * An account e-mail was requested (ADR-031): by the person (forgotten password, new verification link), by a manager
 * (new password link) or by the platform. Carries neither the address nor the link: the link is created when the
 * e-mail is sent, after this event is committed. {@code requestedBy} is {@link Actor#platform()} for anonymous public
 * requests.
 */
public record AccountEmailRequestedV1(PersonId personId, AccountEmailKind kind, Actor requestedBy,
                                      Instant occurredAt) {
}
