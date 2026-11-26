package com.almaraama.maintenance.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Out ports of the module (H1 to H10). Nothing is deleted (M2). */
public final class MaintenanceRepositories {

    private MaintenanceRepositories() {
    }

    /** Issue categories, types and their settings per site (H2, H4, H8). */
    public interface IssueTypes {

        /** Loads a category. */
        Optional<IssueCategory> category(UUID id);

        /** The platform's categories, and the organization's when given; by sort order. */
        List<IssueCategory> categories(Optional<OrganizationId> organization, boolean includeArchived);

        /** Stores a category. */
        IssueCategory save(IssueCategory category);

        /** Loads a type. */
        Optional<IssueType> type(UUID id);

        /** Several types. */
        List<IssueType> types(Collection<UUID> ids);

        /** The types of these categories visible to the organization (platform only when empty). */
        List<IssueType> typesOf(Collection<UUID> categoryIds, Optional<OrganizationId> organization,
                                boolean includeArchived);

        /** Whether a visible category (or a platform one) has the code. */
        boolean categoryCodeTaken(Optional<OrganizationId> organization, String code);

        /** Whether a visible type (or a platform one) has the code. */
        boolean typeCodeTaken(Optional<OrganizationId> organization, String code);

        /** Stores a type. */
        IssueType save(IssueType type);

        /** The setting of a type on a site. */
        Optional<IssueTypeConfig> config(SiteId site, UUID issueTypeId);

        /** The settings of a site. */
        List<IssueTypeConfig> configs(SiteId site);

        /** Stores a setting. */
        IssueTypeConfig save(IssueTypeConfig config);
    }

    /**
     * What a reader may see, per scope (ADR-037): the relations and the permissions computed from the {@code
     * Perimeter}. A ticket is visible when its site is in one rule and one condition of that rule holds, or when the
     * reader asked for it or is assigned to it on a site of the perimeter.
     *
     * @param siteRules       rules of the sites with a role or an access
     * @param organizationRules rules of the organizations with a role on themselves (all their sites)
     * @param person          the reader
     */
    public record Readership(List<SiteRule> siteRules, List<OrganizationRule> organizationRules, UUID person) {

        /**
         * One site.
         *
         * @param commonAll    {@code ticket:read}: every common ticket
         * @param privateAll   {@code ticket:read-private}: every private ticket
         * @param member       active access: common tickets of the allowed buildings
         * @param buildings    allowed buildings when restricted (C7); empty means all
         * @param units        units occupied: their private tickets
         */
        public record SiteRule(SiteId siteId, boolean commonAll, boolean privateAll, boolean member,
                               Set<UUID> buildings, Set<UUID> units) {
        }

        /** One organization seen as a whole. */
        public record OrganizationRule(OrganizationId organizationId, boolean commonAll, boolean privateAll) {
        }
    }

    /** Filters of the ticket list. */
    public record TicketFilter(Optional<SiteId> siteId, String view, List<String> categories,
                               Optional<UUID> issueTypeId, Optional<UUID> buildingId, Optional<UUID> assetId,
                               Optional<String> reference) {
    }

    /** Tickets, photos, assignments, activities. */
    public interface Tickets {

        /** Loads a ticket. */
        Optional<Ticket> findById(UUID id);

        /** One page of the tickets the reader may see, newest change first. */
        PageResult<Ticket> search(Readership readership, TicketFilter filter, PageQuery page);

        /** The next readable reference of an organization (atomic counter). */
        String nextReference(OrganizationId organization);

        /** Stores a ticket. */
        Ticket save(Ticket ticket);

        /** The photos of a ticket. */
        List<TicketPhoto> photos(UUID ticketId);

        /** Number of photos of several tickets. */
        java.util.Map<UUID, Long> photoCounts(Collection<UUID> ticketIds);

        /** Stores a photo. */
        TicketPhoto save(TicketPhoto photo);

        /** The active assignments of a ticket. */
        List<TicketAssignment> assignments(UUID ticketId);

        /** Whether the person is actively assigned to the ticket. */
        boolean assigned(UUID ticketId, PersonId person);

        /** Stores an assignment. */
        TicketAssignment save(TicketAssignment assignment);

        /** The activities of a ticket, oldest first. */
        List<TicketActivity> activities(UUID ticketId);

        /** Appends an activity. */
        TicketActivity save(TicketActivity activity);
    }
}
