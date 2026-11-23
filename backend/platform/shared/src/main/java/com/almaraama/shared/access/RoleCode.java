package com.almaraama.shared.access;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Code of a role (ADR-026, revision 2). Roles are data, the rows of the role table; the specification's roles
 * ({@code ORG_ADMIN}, {@code SITE_MANAGER}, {@code TECHNICIAN}, {@code RESIDENT}, {@code PLATFORM_ADMIN}) are its
 * first rows and custom roles come later. Business code checks permissions, never role codes (section 3.1, criterion
 * 17); a code only names a role in data, in the seeding of the platform, and in the audit log.
 *
 * @param value upper-case letters, digits and underscores, starting with a letter, 2 to 50 characters
 */
public record RoleCode(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    /** Rejects a code that does not have the expected shape. */
    public RoleCode {
        Objects.requireNonNull(value, "value");
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid role code");
        }
    }

    /** Reads a code from text, for example a path parameter; {@code IllegalArgumentException} when malformed. */
    public static RoleCode of(String value) {
        return new RoleCode(value);
    }

    /** The code itself. */
    @Override
    public String toString() {
        return value;
    }
}
