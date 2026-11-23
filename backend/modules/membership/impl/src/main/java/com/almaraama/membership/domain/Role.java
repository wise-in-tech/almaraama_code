package com.almaraama.membership.domain;

import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.id.OrganizationId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A role, a row of the role table (C1, ADR-026 revision 2). The specification's roles are the first rows; custom
 * roles of an organization come later ({@code organizationId}). Business code never tests a role code: it tests
 * permissions, and uses the role's kind only to know who may give it (RB-1).
 *
 * @param code       identifier, such as {@code SITE_MANAGER}
 * @param kind       who may give it (RB-1)
 * @param scopes     where it can be given (RB-3): the promoteur on an organization, the others on a site (C2, C3)
 * @param grantsAll  every permission of the organization, present and future, never adjusted (C8): the promoteur
 * @param organizationId empty for a platform role; the owner of a custom role (later)
 * @param name       names in the three languages (moved to the text dictionary with ADR-029)
 * @param sortOrder  display order
 */
public record Role(RoleCode code, RoleKind kind, Set<ScopeType> scopes, boolean grantsAll,
                   Optional<OrganizationId> organizationId, Name name, int sortOrder) {

    /** A role name in the three interface languages (M1). */
    public record Name(String ar, String en, String fr) {
    }

    /** Copies the sets and checks the mandatory parts. */
    public Role {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(kind, "kind");
        scopes = Set.copyOf(scopes);
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(name, "name");
    }

    /** Whether the role can be given on this kind of scope (RB-3). */
    public boolean appliesTo(ScopeType scope) {
        return scopes.contains(scope);
    }

    /**
     * Whether an organization may change the role's permissions and adjust them person by person: never a {@code
     * grantsAll} role (C8) nor a platform role.
     */
    public boolean editable() {
        return !grantsAll && kind != RoleKind.PLATFORM;
    }
}
