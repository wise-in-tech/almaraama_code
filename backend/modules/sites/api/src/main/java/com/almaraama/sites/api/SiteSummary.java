package com.almaraama.sites.api;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;

/** Public view of a site for other modules. */
public record SiteSummary(SiteId id, OrganizationId organizationId, String name, boolean active) {
}
