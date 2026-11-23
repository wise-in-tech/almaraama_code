package com.almaraama.sites.api;

import com.almaraama.shared.id.OrganizationId;

/** Public view of an organization for other modules, with its country (A1, M4) and currency (M5). */
public record OrganizationSummary(OrganizationId id, OrganizationType type, String name, String countryCode,
                                  String currencyCode, boolean active) {
}
