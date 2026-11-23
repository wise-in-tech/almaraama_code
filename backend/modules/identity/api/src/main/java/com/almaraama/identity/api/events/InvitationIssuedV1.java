package com.almaraama.identity.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/**
 * An account was created by a manager, or an invited account received a new "set my password" link (D4). Carries no
 * link: it is created and sent by e-mail after this event is committed (ADR-031).
 */
public record InvitationIssuedV1(PersonId personId, Actor issuedBy, Instant occurredAt) {
}
