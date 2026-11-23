package com.almaraama.identity.application;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.api.AccountStatus;
import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.Contact;
import com.almaraama.identity.api.InvitationIssued;
import com.almaraama.identity.api.RegisterSelf;
import com.almaraama.identity.api.InvitePerson;
import com.almaraama.identity.api.PasswordLinkIssued;
import com.almaraama.identity.api.PersonSummary;
import com.almaraama.identity.api.events.InvitationIssuedV1;
import com.almaraama.identity.domain.Credential;
import com.almaraama.identity.domain.CredentialRepository;
import com.almaraama.identity.domain.EmailAddress;
import com.almaraama.identity.domain.PasswordHasher;
import com.almaraama.identity.domain.PasswordPolicy;
import com.almaraama.identity.domain.Person;
import com.almaraama.identity.domain.PersonRepository;
import com.almaraama.identity.domain.PhoneNumber;
import com.almaraama.identity.domain.PhoneNumberParser;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.logging.LogMasking;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the public port of the module (CF-1): create accounts by e-mail, send password links, read public
 * profiles (D1, D4, D5, ADR-030).
 */
@Service
class IdentityService implements IdentityApi {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityService.class);

    private final PersonRepository persons;
    private final PhoneNumberParser phoneNumbers;
    private final AccountEmails emails;
    private final CredentialRepository credentials;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the identity module's public port. */
    IdentityService(PersonRepository persons, PhoneNumberParser phoneNumbers, AccountEmails emails,
                    CredentialRepository credentials, PasswordPolicy passwordPolicy, PasswordHasher passwordHasher,
                    ApplicationEventPublisher events, Clock clock) {
        this.persons = persons;
        this.phoneNumbers = phoneNumbers;
        this.emails = emails;
        this.credentials = credentials;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates the account when the e-mail is unknown and requests a "set my password" e-mail; an {@code INVITED}
     * account gets a new link; an active or pending one gets nothing. {@code ACCOUNT_DEACTIVATED} for a deactivated
     * account (D7), {@code EMAIL_INVALID}, {@code PHONE_INVALID} for bad input.
     */
    @Override
    @Transactional
    public InvitationIssued invitePerson(InvitePerson command) {
        Instant now = clock.instant();
        EmailAddress email = EmailAddress.parse(command.email());
        PhoneNumber phone = phoneNumbers.parse(command.phone());
        Optional<Person> existing = persons.findByEmail(email);
        Person person = existing.orElseGet(() -> persons.save(Person.invite(
                command.firstName(), command.lastName(), email, phone, command.language(), now)));

        if (person.isDeactivated()) {
            throw deactivated();
        }
        if (person.status() != AccountStatus.INVITED) {
            LOG.debug("Invitation of {}: account {} already exists", LogMasking.email(email.value()),
                    person.id().value());
            return InvitationIssued.existingAccount(person.id());
        }
        events.publishEvent(new InvitationIssuedV1(person.id(), command.issuedBy(), now));
        Instant expiresAt = emails.request(person, AccountEmailKind.SET_PASSWORD, command.issuedBy(), now);
        LOG.debug("Person {} invited ({}), new account: {}", person.id().value(), LogMasking.email(email.value()),
                existing.isEmpty());
        return new InvitationIssued(person.id(), Optional.of(expiresAt));
    }

    /**
     * Requests a new "set my password" link for an {@code INVITED} account, a reset link for an active one; {@code
     * NOT_FOUND} for an unknown person, {@code ACCOUNT_DEACTIVATED} for a deactivated one, {@code
     * ACCOUNT_PENDING_VERIFICATION} while the e-mail is not verified.
     */
    @Override
    @Transactional
    public PasswordLinkIssued sendPasswordLink(PersonId personId, Actor requestedBy) {
        Instant now = clock.instant();
        Person person = persons.findById(personId).orElseThrow(
                () -> new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "Unknown person"));
        AccountEmailKind kind = switch (person.status()) {
            case INVITED -> AccountEmailKind.SET_PASSWORD;
            case ACTIVE -> AccountEmailKind.PASSWORD_RESET;
            case DEACTIVATED -> throw deactivated();
            case PENDING_VERIFICATION -> throw new BusinessException("ACCOUNT_PENDING_VERIFICATION",
                    ErrorCategory.RULE_VIOLATION, "The e-mail is not verified yet");
        };
        Instant expiresAt = emails.request(person, kind, requestedBy, now);
        return new PasswordLinkIssued(kind, expiresAt);
    }

    /**
     * D2, ADR-034: checks the input first (password rules, e-mail and phone formats), so that errors never depend on
     * whether the e-mail has an account. Then either creates the account with its password and requests the
     * verification link, or, for a known e-mail, requests the "you already have an account" e-mail. The password is
     * hashed in both cases, so both take the same time.
     */
    @Override
    @Transactional
    public Optional<PersonId> registerSelf(RegisterSelf command) {
        Instant now = clock.instant();
        EmailAddress email = EmailAddress.parse(command.email());
        PhoneNumber phone = phoneNumbers.parse(command.phone());
        String password = passwordPolicy.check(command.password(), email);
        String hash = passwordHasher.hash(password);
        Optional<Person> existing = persons.findByEmail(email);
        if (existing.isPresent()) {
            emails.request(existing.get(), AccountEmailKind.ALREADY_REGISTERED, Actor.platform(), now);
            LOG.debug("Self-registration with a known e-mail ({}): account {} unchanged",
                    LogMasking.email(email.value()), existing.get().id().value());
            return Optional.empty();
        }
        Person person = persons.save(Person.register(command.firstName(), command.lastName(), email, phone,
                command.language(), now));
        credentials.save(Credential.create(person.id(), hash, now));
        emails.request(person, AccountEmailKind.EMAIL_VERIFICATION, Actor.platform(), now);
        LOG.debug("Person {} registered ({}), waiting for e-mail verification", person.id().value(),
                LogMasking.email(email.value()));
        return Optional.of(person.id());
    }

    /** D3: the e-mail trimmed and lower-cased, the phone in E.164; {@code EMAIL_INVALID}, {@code PHONE_INVALID}. */
    @Override
    public Contact checkContact(String email, String phone) {
        return new Contact(EmailAddress.parse(email).value(), phoneNumbers.parse(phone).value());
    }

    /** The public profile of a person. */
    @Override
    @Transactional(readOnly = true)
    public Optional<PersonSummary> findPerson(PersonId personId) {
        return persons.findById(personId).map(IdentityService::summary);
    }

    /** The public profiles of several persons, in one query. */
    @Override
    @Transactional(readOnly = true)
    public List<PersonSummary> findPersons(Collection<PersonId> personIds) {
        return persons.findAllById(personIds).stream().map(IdentityService::summary).toList();
    }

    /** The person who signs in with this e-mail; empty for an unknown or malformed e-mail. */
    @Override
    @Transactional(readOnly = true)
    public Optional<PersonSummary> findPersonByEmail(String email) {
        return EmailAddress.tryParse(email).flatMap(persons::findByEmail).map(IdentityService::summary);
    }

    /** The public profile of a person, without any credential. */
    private static PersonSummary summary(Person p) {
        return new PersonSummary(p.id(), p.firstName(), p.lastName(), p.email().value(), p.phone().value(),
                p.language(), p.status(), p.emailVerifiedAt().orElse(null));
    }

    /** A deactivated account cannot be invited or receive a link (D7). */
    private static BusinessException deactivated() {
        return new BusinessException("ACCOUNT_DEACTIVATED", ErrorCategory.RULE_VIOLATION, "Account deactivated (D7)");
    }
}
