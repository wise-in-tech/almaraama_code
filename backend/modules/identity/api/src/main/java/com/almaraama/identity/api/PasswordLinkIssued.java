package com.almaraama.identity.api;

import com.almaraama.identity.api.events.AccountEmailKind;
import java.time.Instant;

/**
 * Result of {@link IdentityApi#sendPasswordLink}: which kind of link was requested and until when it is valid. The
 * link itself goes only to the person's e-mail.
 */
public record PasswordLinkIssued(AccountEmailKind kind, Instant expiresAt) {
}
