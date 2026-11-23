package com.almaraama.identity.adapters.out.persistence;

import com.almaraama.identity.domain.Person;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Spring Data repository of persons, schema identity. Extends {@link Repository}: no delete method exists (M2). */
interface SpringDataPersons extends Repository<Person, UUID> {

    /** Loads a person. */
    Optional<Person> findById(UUID id);

    /** Loads a person with a row lock ({@code SELECT … FOR UPDATE}) held until the end of the transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Person p where p.id = :id")
    Optional<Person> findLockedById(UUID id);

    /** The person with this normalized e-mail (unique index {@code person_email_uq}). */
    Optional<Person> findByEmail(String email);

    /** Several persons in one query. */
    List<Person> findByIdIn(Collection<UUID> ids);

    /** Inserts or updates a person. */
    Person save(Person person);
}
