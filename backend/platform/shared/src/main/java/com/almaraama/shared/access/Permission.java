package com.almaraama.shared.access;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogue of permissions (C1, C11, ADR-026 revision 2), {@code resource:action} in English; the table to the
 * specification's French names is in ADR-026 and the glossary. Defined by the code because each endpoint checks one
 * precise permission; which role or person holds which permission is data. Each sprint adds its own permissions here
 * together with their default rows (RB-6).
 * <p>
 * A permission declares the scopes it applies to and whether it is adjustable. Non-adjustable permissions come only
 * from a {@code grantsAll} role (the promoteur) or a platform role, and can never be added or removed by a difference
 * or an adjustment: this is what makes the power to change permissions non-delegable (C9).
 */
public enum Permission {

    // ---- platform (section 3.5): our team only, never adjustable
    ORGANIZATION_READ("organization:read", false, ScopeType.PLATFORM),
    ORGANIZATION_CREATE("organization:create", false, ScopeType.PLATFORM),
    ORGANIZATION_DEACTIVATE("organization:deactivate", false, ScopeType.PLATFORM),
    /** Name and contact of the organization: the promoteur on its own, our team on any. */
    ORGANIZATION_UPDATE("organization:update", false, ScopeType.PLATFORM, ScopeType.ORGANIZATION),

    // ---- structure (A1 to A9): residence, buildings, outdoor areas, zones
    SITE_CREATE("site:create", true, ScopeType.ORGANIZATION),
    SITE_READ("site:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    SITE_UPDATE("site:update", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    SITE_ARCHIVE("site:archive", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    BUILDING_CREATE("building:create", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    BUILDING_READ("building:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    BUILDING_UPDATE("building:update", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    BUILDING_ARCHIVE("building:archive", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    OUTDOOR_AREA_CREATE("outdoor-area:create", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    OUTDOOR_AREA_READ("outdoor-area:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    OUTDOOR_AREA_UPDATE("outdoor-area:update", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    OUTDOOR_AREA_ARCHIVE("outdoor-area:archive", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ZONE_CREATE_PROVISIONAL("zone:create-provisional", true, ScopeType.ORGANIZATION),
    REGISTRATION_QR_READ("registration-qr:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    REGISTRATION_QR_MANAGE("registration-qr:manage", true, ScopeType.ORGANIZATION, ScopeType.SITE),

    // ---- units and their categories (A6, A7)
    UNIT_CATEGORY_CREATE("unit-category:create", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    UNIT_CATEGORY_READ("unit-category:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    UNIT_CATEGORY_UPDATE("unit-category:update", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    UNIT_CATEGORY_ARCHIVE("unit-category:archive", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    UNIT_CREATE("unit:create", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    UNIT_READ("unit:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    UNIT_UPDATE("unit:update", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    UNIT_ARCHIVE("unit:archive", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    UNIT_IMPORT("unit:import", true, ScopeType.ORGANIZATION, ScopeType.SITE),

    // ---- accounts and accesses (D1 to D7)
    ACCOUNT_CREATE("account:create", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ACCOUNT_READ("account:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ACCOUNT_UPDATE("account:update", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ACCOUNT_DEACTIVATE("account:deactivate", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ACCOUNT_INVITE("account:invite", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ACCESS_APPROVE("access:approve", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ACCESS_REFUSE("access:refuse", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ACCESS_RESTRICT("access:restrict", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    ACCESS_REVOKE("access:revoke", true, ScopeType.ORGANIZATION, ScopeType.SITE),

    // ---- roles and permissions (C8, C9): the promoteur only, never adjustable
    ROLE_ASSIGNMENT_GRANT("role-assignment:grant", false, ScopeType.ORGANIZATION, ScopeType.SITE),
    ROLE_ASSIGNMENT_REVOKE("role-assignment:revoke", false, ScopeType.ORGANIZATION, ScopeType.SITE),
    PERMISSION_ADJUSTMENT_MANAGE("permission-adjustment:manage", false, ScopeType.ORGANIZATION),

    // ---- occupancies (E1 to E5)
    OCCUPANCY_CREATE("occupancy:create", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    OCCUPANCY_READ("occupancy:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    OCCUPANCY_END("occupancy:end", true, ScopeType.ORGANIZATION, ScopeType.SITE),

    // ---- sprint 2: pre-registrations (D3, ADR-034)
    PRE_REGISTRATION_CREATE("pre-registration:create", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    PRE_REGISTRATION_READ("pre-registration:read", true, ScopeType.ORGANIZATION, ScopeType.SITE),
    PRE_REGISTRATION_CANCEL("pre-registration:cancel", true, ScopeType.ORGANIZATION, ScopeType.SITE),

    // ---- sprint 2: platform back-office (section 3.5, C12, ADR-035), never adjustable
    REFERENCE_DATA_READ("reference-data:read", false, ScopeType.PLATFORM),
    REFERENCE_DATA_CREATE("reference-data:create", false, ScopeType.PLATFORM),
    REFERENCE_DATA_UPDATE("reference-data:update", false, ScopeType.PLATFORM),
    REFERENCE_DATA_ARCHIVE("reference-data:archive", false, ScopeType.PLATFORM),
    REFERENCE_DATA_VALIDATE_PROVISIONAL("reference-data:validate-provisional", false, ScopeType.PLATFORM),
    TEXT_READ("text:read", false, ScopeType.PLATFORM),
    TEXT_UPDATE("text:update", false, ScopeType.PLATFORM),
    SUPPORT_ACCESS("support:access", false, ScopeType.PLATFORM);

    private final String code;
    private final boolean adjustable;
    private final Set<ScopeType> scopes;

    /**
     * @param code       {@code resource:action}, as in the contract
     * @param adjustable whether a role difference or a person adjustment may add or remove it (C9)
     * @param first      a scope the permission applies to; {@code others} are the remaining ones
     */
    Permission(String code, boolean adjustable, ScopeType first, ScopeType... others) {
        this.code = code;
        this.adjustable = adjustable;
        this.scopes = EnumSet.of(first, others);
    }

    /** {@code resource:action}, as in the contract and the database. */
    public String code() {
        return code;
    }

    /** Whether a difference of a role or an adjustment of a person may add or remove it (C9, section 3.6). */
    public boolean adjustable() {
        return adjustable;
    }

    /** Whether the permission is flagged on the screens when adjusted (section 3.6); none in sprint 1. */
    public boolean sensitive() {
        return false;
    }

    /** The scopes this permission applies to (RB-3). */
    public Set<ScopeType> scopes() {
        return Set.copyOf(scopes);
    }

    /** Whether the permission applies to this kind of scope. */
    public boolean appliesTo(ScopeType scope) {
        return scopes.contains(scope);
    }

    /** The permission with this code, if the catalogue knows it. */
    public static Optional<Permission> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }

    /**
     * Every permission that applies to an organization or its sites: what a {@code grantsAll} role (the promoteur)
     * holds, present and future (C8).
     */
    public static Set<Permission> organizationWide() {
        EnumSet<Permission> all = EnumSet.noneOf(Permission.class);
        for (Permission permission : values()) {
            if (permission.appliesTo(ScopeType.ORGANIZATION) || permission.appliesTo(ScopeType.SITE)) {
                all.add(permission);
            }
        }
        return all;
    }
}
