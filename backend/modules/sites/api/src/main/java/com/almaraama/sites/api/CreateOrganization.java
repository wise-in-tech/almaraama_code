package com.almaraama.sites.api;

import com.almaraama.shared.actor.Actor;
import java.util.Objects;

/**
 * A new client organization (A1, C12), created when we onboard a client. {@code countryCode} (ISO 3166) drives its
 * currency, hosting and addresses (M4, M5) and must be loaded in the reference data.
 */
public record CreateOrganization(OrganizationType type, String name, String countryCode, String contactPhone,
                                 String contactEmail, Actor createdBy) {

    /** Type, name, country and author are mandatory. */
    public CreateOrganization {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(createdBy, "createdBy");
    }
}
