package com.almaraama.identity.domain;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A single-use account link that expires (ADR-030): "set my password" (D4), e-mail verification (D1) or password
 * reset (D5). Only the SHA-256 hash of its secret is stored.
 */
@Entity
@Table(schema = "identity", name = "account_token")
public class AccountToken {

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private AccountEmailKind purpose;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "issued_by_type", nullable = false)
    private ActorType issuedByType;

    @Column(name = "issued_by_id", nullable = false)
    private UUID issuedById;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected AccountToken() {
    }

    /**
     * A new single-use link valid for {@code ttl}; only the hash of its secret is kept. {@code purpose} must be a kind
     * of e-mail that carries a link.
     */
    public static AccountToken issue(PersonId personId, AccountEmailKind purpose, String tokenHash, Actor issuedBy,
                                     Instant now, Duration ttl) {
        if (!purpose.carriesLink()) {
            throw new IllegalArgumentException("No link for " + purpose);
        }
        AccountToken token = new AccountToken();
        token.id = UuidV7.next();
        token.personId = personId.value();
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.issuedByType = issuedBy.type();
        token.issuedById = issuedBy.id();
        token.issuedAt = now;
        token.expiresAt = now.plus(ttl);
        return token;
    }

    /** Not used, not revoked and not expired. */
    public boolean isUsable(Instant now) {
        return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }

    /** Uses the link once; fails if it is no longer usable. */
    public void markUsed(Instant now) {
        if (!isUsable(now)) {
            throw new IllegalStateException("Link already used, revoked or expired");
        }
        usedAt = now;
    }

    /** A new link of the same purpose was issued for the person: this one stops working (ADR-030). */
    public void revoke(Instant now) {
        if (revokedAt == null && usedAt == null) {
            revokedAt = now;
        }
    }

    /** Identifier of the link. */
    public UUID id() {
        return id;
    }

    /** The person the link belongs to. */
    public PersonId personId() {
        return new PersonId(personId);
    }

    /** What the link is for. */
    public AccountEmailKind purpose() {
        return purpose;
    }

    /** End of validity of the link. */
    public Instant expiresAt() {
        return expiresAt;
    }
}
