package com.almaraama.identity.domain;

import com.almaraama.shared.id.PersonId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Out port: persons (CF-8, the domain does not know how they are stored). */
public interface PersonRepository {

    /** Loads a person. */
    Optional<Person> findById(PersonId id);

    /**
     * Loads a person and locks the row until the end of the transaction, so that concurrent work on the same person
     * (the account e-mails and their hourly limit, ADR-031) runs one at a time.
     */
    Optional<Person> lockById(PersonId id);

    /** The person who signs in with this e-mail (unique, D1). */
    Optional<Person> findByEmail(EmailAddress email);

    /** Several persons; unknown identifiers are left out. */
    List<Person> findAllById(Collection<PersonId> ids);

    /** Stores a person. */
    Person save(Person person);
}
