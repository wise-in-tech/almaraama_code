package com.almaraama.identity.adapters.out.persistence;

import com.almaraama.identity.domain.Credential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data repository of credentials, schema identity. Extends {@link Repository}: no delete method exists (M2). */
interface SpringDataCredentials extends Repository<Credential, UUID> {
    /** The credential of a person, keyed by the person's identifier. */
    Optional<Credential> findById(UUID personId);

    /** Inserts or updates a credential. */
    Credential save(Credential credential);
}
