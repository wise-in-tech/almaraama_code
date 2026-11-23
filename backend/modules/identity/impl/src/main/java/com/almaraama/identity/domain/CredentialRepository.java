package com.almaraama.identity.domain;

import com.almaraama.shared.id.PersonId;
import java.util.Optional;

/** Out port: credentials. */
public interface CredentialRepository {

    /** The PIN credential of a person, absent until the PIN is chosen. */
    Optional<Credential> findByPersonId(PersonId personId);

    /** Stores a credential with its failure counter and lock. */
    Credential save(Credential credential);
}
