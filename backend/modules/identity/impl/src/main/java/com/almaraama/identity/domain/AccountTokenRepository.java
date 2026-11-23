package com.almaraama.identity.domain;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.shared.id.PersonId;
import java.util.List;
import java.util.Optional;

/** Out port: account links, found by the hash of their secret (ADR-030). */
public interface AccountTokenRepository {

    /** A link by the hash of its secret, whatever its state. */
    Optional<AccountToken> findByTokenHash(String tokenHash);

    /** Links of a person for this purpose, neither used nor revoked, expired or not. */
    List<AccountToken> findOpen(PersonId personId, AccountEmailKind purpose);

    /** Stores a link. */
    AccountToken save(AccountToken token);
}
