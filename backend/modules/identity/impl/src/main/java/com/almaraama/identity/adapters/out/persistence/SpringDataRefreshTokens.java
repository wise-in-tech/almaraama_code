package com.almaraama.identity.adapters.out.persistence;

import com.almaraama.identity.domain.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data repository of refreshtokens, schema identity. Extends {@link Repository}: no delete method exists (M2). */
interface SpringDataRefreshTokens extends Repository<RefreshToken, UUID> {
    /** A refresh token by the hash of its secret, used or not. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Inserts or updates a refresh token. */
    RefreshToken save(RefreshToken token);
}
