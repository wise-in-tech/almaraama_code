package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.MembershipStatus;
import com.almaraama.membership.domain.SiteMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Site accesses, schema membership. No delete (M2). */
interface SpringDataSiteMemberships extends Repository<SiteMembership, UUID> {

    /** Loads a site access. */
    Optional<SiteMembership> findById(UUID id);

    /** Every access of a person, newest first. */
    List<SiteMembership> findByPersonIdOrderByCreatedAtDesc(UUID personId);

    /** The person's access to the site, except in the given status. */
    Optional<SiteMembership> findByPersonIdAndSiteIdAndStatusNot(UUID personId, UUID siteId, MembershipStatus not);

    /** Every access of a site, newest first. */
    List<SiteMembership> findBySiteIdOrderByCreatedAtDesc(UUID siteId);

    /** Accesses of a site with one status, newest first. */
    List<SiteMembership> findBySiteIdAndStatusOrderByCreatedAtDesc(UUID siteId, MembershipStatus status);

    /** Accesses with one status in an organization's sites. */
    List<SiteMembership> findByOrganizationIdAndStatus(UUID organizationId, MembershipStatus status);

    /** Inserts or updates a site access. */
    SiteMembership save(SiteMembership membership);
}
