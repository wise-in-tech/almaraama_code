package com.almaraama.shared.actor;

import com.almaraama.shared.id.PersonId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who performs an action. Recorded with every change and every audit entry (M2, H9).
 *
 * @param supportSessionId set when a platform administrator acts inside an organization during a support session
 *                         (C12, ADR-035): every event and audit entry then carries the session; {@code null} otherwise
 */
public record Actor(ActorType type, UUID id, UUID supportSessionId) {

    /** Identifier of the platform itself, for actions no person triggered (scheduled jobs, seeding). */
    public static final UUID PLATFORM_ID = UUID.fromString("00000000-0000-7000-8000-000000000000");

    public Actor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    /** An actor outside any support session (the usual case). */
    public Actor(ActorType type, UUID id) {
        this(type, id, null);
    }

    /** A person acting through the application. */
    public static Actor person(PersonId personId) {
        return new Actor(ActorType.PERSON, personId.value());
    }

    /** The platform itself (scheduled jobs, seeding, public flows nobody is signed in for). */
    public static Actor platform() {
        return new Actor(ActorType.SYSTEM, PLATFORM_ID);
    }

    /** The same actor acting inside this support session (C12, ADR-035). */
    public Actor inSupportSession(UUID sessionId) {
        return new Actor(type, id, Objects.requireNonNull(sessionId, "sessionId"));
    }

    /** The support session this actor acts in, if any (ADR-035). */
    public Optional<UUID> supportSession() {
        return Optional.ofNullable(supportSessionId);
    }
}
