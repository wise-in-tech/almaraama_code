package com.almaraama.identity.adapters.out.persistence;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.domain.AccountToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data repository of account links, schema identity. Extends {@link Repository}: no delete method (M2). */
interface SpringDataAccountTokens extends Repository<AccountToken, UUID> {

    /** A link by the hash of its secret. */
    Optional<AccountToken> findByTokenHash(String tokenHash);

    /** The person's links of one purpose, neither used nor revoked. */
    List<AccountToken> findByPersonIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(UUID personId,
                                                                              AccountEmailKind purpose);

    /** Inserts or updates a link. */
    AccountToken save(AccountToken token);
}
