package com.almaraama.sites.api;

import com.almaraama.shared.id.OrganizationId;
import java.time.Instant;

/**
 * An organization as the platform back-office lists it (C12, ADR-035): its public data, contact, state and number of
 * sites; never its people nor its units.
 */
public record OrganizationAdminView(OrganizationId id, OrganizationType type, String name, String countryCode,
                                    String currencyCode, String contactPhone, String contactEmail, boolean active,
                                    long version, long siteCount, Instant createdAt, Instant deactivatedAt) {
}
