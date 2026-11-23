package com.almaraama.identity.application;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.domain.AccountToken;
import com.almaraama.identity.domain.AccountTokenRepository;
import com.almaraama.identity.domain.Person;
import com.almaraama.identity.domain.PersonRepository;
import com.almaraama.identity.domain.SecretTokens;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.time.Instant;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Finds the account behind a link secret (ADR-030). Unknown, expired, used, revoked, of another purpose, or for an
 * account no longer in the expected state: always the same {@code LINK_INVALID_OR_EXPIRED} answer.
 */
@Component
class AccountLinks {

    private final AccountTokenRepository tokens;
    private final PersonRepository persons;

    /**
     * @param tokens  account links
     * @param persons their owners
     */
    AccountLinks(AccountTokenRepository tokens, PersonRepository persons) {
        this.tokens = tokens;
        this.persons = persons;
    }

    /** A usable link and its person. */
    record Opened(AccountToken token, Person person) {
    }

    /**
     * Opens a link of this purpose whose person passes {@code accepts}; throws {@code LINK_INVALID_OR_EXPIRED}
     * otherwise. Does not use the link.
     */
    Opened open(String secret, AccountEmailKind purpose, Predicate<Person> accepts, Instant now) {
        if (secret == null || secret.isBlank()) {
            throw invalid();
        }
        AccountToken token = tokens.findByTokenHash(SecretTokens.hash(secret))
                .filter(candidate -> candidate.purpose() == purpose && candidate.isUsable(now))
                .orElseThrow(AccountLinks::invalid);
        Person person = persons.findById(token.personId()).filter(accepts).orElseThrow(AccountLinks::invalid);
        return new Opened(token, person);
    }

    /** Marks the link used, once (ADR-030). */
    void use(Opened opened, Instant now) {
        opened.token().markUsed(now);
        tokens.save(opened.token());
    }

    /** The single answer for every unusable link, so nothing is revealed. */
    static BusinessException invalid() {
        return new BusinessException("LINK_INVALID_OR_EXPIRED", ErrorCategory.NOT_FOUND, "Link not usable");
    }
}
