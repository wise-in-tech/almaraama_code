package com.almaraama.identity.api;

import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/**
 * Public profile of a person (D1). No credential, device, link or token ever leaves the identity module.
 * {@code emailVerifiedAt} is null while the e-mail is not verified.
 */
public record PersonSummary(PersonId id, String firstName, String lastName, String email, String phone,
                            Language language, AccountStatus status, Instant emailVerifiedAt) {
}
