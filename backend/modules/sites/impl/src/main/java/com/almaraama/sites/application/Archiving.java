package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;

/**
 * Archiving is its own permission (section 3.2, {@code *:archive}): changing the {@code active} flag of a site,
 * building, outdoor area, unit or category, in either direction, needs it on top of the update permission. Nothing is
 * deleted (M2).
 */
final class Archiving {

    /** Static rule only. */
    private Archiving() {
    }

    /** {@code PERMISSION_DENIED} when {@code active} changes and the caller lacks the archive permission. */
    static void requireIfChanged(Perimeter perimeter, Permission archive, boolean wasActive, boolean active,
                                 OrganizationId organization, SiteId site) {
        if (wasActive != active) {
            perimeter.require(archive, organization, site);
        }
    }
}
