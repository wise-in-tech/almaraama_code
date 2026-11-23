package com.almaraama.identity.adapters.out.persistence;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.domain.AccountToken;
import com.almaraama.identity.domain.AccountTokenRepository;
import com.almaraama.identity.domain.Credential;
import com.almaraama.identity.domain.CredentialRepository;
import com.almaraama.identity.domain.DeviceSessionRepository;
import com.almaraama.identity.domain.EmailAddress;
import com.almaraama.identity.domain.EmailDispatch;
import com.almaraama.identity.domain.EmailDispatchRepository;
import com.almaraama.identity.domain.Person;
import com.almaraama.identity.domain.PersonRepository;
import com.almaraama.identity.domain.RefreshToken;
import com.almaraama.identity.domain.TrustedDevice;
import com.almaraama.shared.id.PersonId;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapters from the domain's out ports to Spring Data JPA, in schema {@code identity} only (CF-3). */
final class JpaIdentityRepositories {

    /** Holder of the adapter classes only. */
    private JpaIdentityRepositories() {
    }

    /** {@link PersonRepository} on the {@code identity.person} table. */
    @Component
    static class JpaPersonRepository implements PersonRepository {
        private final SpringDataPersons persons;

        /** @param persons Spring Data repository of the person table */
        JpaPersonRepository(SpringDataPersons persons) {
            this.persons = persons;
        }

        /** Loads a person by identifier. */
        @Override
        public Optional<Person> findById(PersonId id) {
            return persons.findById(id.value());
        }

        /** Loads a person with a row lock held until the end of the transaction. */
        @Override
        public Optional<Person> lockById(PersonId id) {
            return persons.findLockedById(id.value());
        }

        /** Loads the person who signs in with this e-mail (unique, D1). */
        @Override
        public Optional<Person> findByEmail(EmailAddress email) {
            return persons.findByEmail(email.value());
        }

        /** Loads several persons in one query; an empty input gives an empty list without querying. */
        @Override
        public List<Person> findAllById(Collection<PersonId> ids) {
            return ids.isEmpty() ? List.of() : persons.findByIdIn(ids.stream().map(PersonId::value).toList());
        }

        /** Inserts or updates a person. */
        @Override
        public Person save(Person person) {
            return persons.save(person);
        }
    }

    /** {@link CredentialRepository} on the {@code identity.credential} table. */
    @Component
    static class JpaCredentialRepository implements CredentialRepository {
        private final SpringDataCredentials credentials;

        /** @param credentials Spring Data repository of the credential table */
        JpaCredentialRepository(SpringDataCredentials credentials) {
            this.credentials = credentials;
        }

        /** The credential of a person, absent until the password is chosen. */
        @Override
        public Optional<Credential> findByPersonId(PersonId personId) {
            return credentials.findById(personId.value());
        }

        /** Inserts or updates a credential, with its failure counter and lock. */
        @Override
        public Credential save(Credential credential) {
            return credentials.save(credential);
        }
    }

    /** {@link AccountTokenRepository} on the {@code identity.account_token} table. */
    @Component
    static class JpaAccountTokenRepository implements AccountTokenRepository {
        private final SpringDataAccountTokens tokens;

        /** @param tokens Spring Data repository of the account links */
        JpaAccountTokenRepository(SpringDataAccountTokens tokens) {
            this.tokens = tokens;
        }

        /** Finds a link by the hash of its secret. */
        @Override
        public Optional<AccountToken> findByTokenHash(String tokenHash) {
            return tokens.findByTokenHash(tokenHash);
        }

        /** The person's links of this purpose neither used nor revoked, so that a new link can revoke them. */
        @Override
        public List<AccountToken> findOpen(PersonId personId, AccountEmailKind purpose) {
            return tokens.findByPersonIdAndPurposeAndUsedAtIsNullAndRevokedAtIsNull(personId.value(), purpose);
        }

        /** Inserts or updates a link. */
        @Override
        public AccountToken save(AccountToken token) {
            return tokens.save(token);
        }
    }

    /** {@link EmailDispatchRepository} on the {@code identity.email_dispatch} table. */
    @Component
    static class JpaEmailDispatchRepository implements EmailDispatchRepository {
        private final SpringDataEmailDispatches dispatches;

        /** @param dispatches Spring Data repository of the e-mail traces */
        JpaEmailDispatchRepository(SpringDataEmailDispatches dispatches) {
            this.dispatches = dispatches;
        }

        /** E-mails of this kind actually sent to the person since the given time. */
        @Override
        public long countSentSince(PersonId personId, AccountEmailKind kind, Instant since) {
            return dispatches.countByPersonIdAndKindAndOutcomeAndCreatedAtAfter(personId.value(), kind,
                    EmailDispatch.Outcome.SENT, since);
        }

        /** Appends a trace. */
        @Override
        public EmailDispatch save(EmailDispatch dispatch) {
            return dispatches.save(dispatch);
        }
    }

    /** {@link DeviceSessionRepository} on the device and refresh token tables. */
    @Component
    static class JpaDeviceSessionRepository implements DeviceSessionRepository {
        private final SpringDataDevices devices;
        private final SpringDataRefreshTokens refreshTokens;

        /**
         * @param devices Spring Data repository of the trusted devices
         * @param refreshTokens Spring Data repository of the refresh tokens
         */
        JpaDeviceSessionRepository(SpringDataDevices devices, SpringDataRefreshTokens refreshTokens) {
            this.devices = devices;
            this.refreshTokens = refreshTokens;
        }

        /** Loads a device. */
        @Override
        public Optional<TrustedDevice> findDevice(UUID deviceId) {
            return devices.findById(deviceId);
        }

        /** The person's devices whose session is not ended. */
        @Override
        public List<TrustedDevice> findActiveDevices(PersonId personId) {
            return devices.findByPersonIdAndRevokedAtIsNull(personId.value());
        }

        /** Inserts or updates a device. */
        @Override
        public TrustedDevice save(TrustedDevice device) {
            return devices.save(device);
        }

        /** Finds a refresh token by the hash of its secret, used or not (reuse detection). */
        @Override
        public Optional<RefreshToken> findRefreshToken(String tokenHash) {
            return refreshTokens.findByTokenHash(tokenHash);
        }

        /** Inserts or updates a refresh token. */
        @Override
        public RefreshToken save(RefreshToken token) {
            return refreshTokens.save(token);
        }
    }
}
