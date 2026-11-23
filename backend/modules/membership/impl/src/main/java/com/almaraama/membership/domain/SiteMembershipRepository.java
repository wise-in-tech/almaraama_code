package com.almaraama.membership.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: site accesses. No delete (M2). */
public interface SiteMembershipRepository {

    /** Loads a site access. */
    Optional<SiteMembership> findById(UUID id);

    /** Every access of a person, newest first. */
    List<SiteMembership> ofPerson(PersonId personId);

    /** The current access of the person to the site: pending, active or deactivated (a rejected one is not current). */
    Optional<SiteMembership> current(PersonId personId, SiteId siteId);

    /** Accesses of a site, newest first, optionally with one status. */
    List<SiteMembership> ofSite(SiteId siteId, MembershipStatus status);

    /** Active accesses in an organization's sites. */
    List<SiteMembership> activeInOrganization(OrganizationId organizationId);

    /** Stores a site access. */
    SiteMembership save(SiteMembership membership);
}
