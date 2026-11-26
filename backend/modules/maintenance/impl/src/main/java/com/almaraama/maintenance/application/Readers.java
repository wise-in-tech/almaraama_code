package com.almaraama.maintenance.application;

import com.almaraama.maintenance.domain.MaintenanceRepositories.Readership;
import com.almaraama.maintenance.domain.MaintenanceRepositories.Readership.OrganizationRule;
import com.almaraama.maintenance.domain.MaintenanceRepositories.Readership.SiteRule;
import com.almaraama.maintenance.domain.Ticket;
import com.almaraama.maintenance.domain.Visibility;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.shared.perimeter.Perimeter.SiteAccess;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who sees which ticket (H8, C3, C4, C7, M3, ADR-037): relations first (requester, occupant of the unit, member of the
 * site for common tickets, assigned technician), widened by {@code ticket:read} (common) and {@code
 * ticket:read-private} (private). The same rules serve the list query and the single ticket.
 */
final class Readers {

    private Readers() {
    }

    /** The rules of a reader for the list query. */
    static Readership of(Perimeter perimeter) {
        List<SiteRule> sites = perimeter.sites().entrySet().stream().map(e -> {
            SiteAccess access = e.getValue();
            boolean restricted = !access.permissions().contains(Permission.UNIT_READ)
                    && !access.allowedBuildingIds().isEmpty();
            return new SiteRule(e.getKey(), access.permissions().contains(Permission.TICKET_READ),
                    access.permissions().contains(Permission.TICKET_READ_PRIVATE), access.member(),
                    restricted ? access.allowedBuildingIds().stream().map(BuildingId::value).collect(Collectors.toSet())
                            : Set.of(),
                    access.occupiedUnitIds().stream().map(UnitId::value).collect(Collectors.toSet()));
        }).toList();
        List<OrganizationRule> organizations = perimeter.organizations().entrySet().stream()
                .map(e -> new OrganizationRule(e.getKey(), e.getValue().permissions().contains(Permission.TICKET_READ),
                        e.getValue().permissions().contains(Permission.TICKET_READ_PRIVATE)))
                .toList();
        return new Readership(sites, organizations, perimeter.actor().id());
    }

    /** Whether the reader sees the ticket at all; {@code assigned} tells whether they are an assigned technician. */
    static boolean visible(Perimeter perimeter, Ticket ticket, boolean assigned) {
        if (!perimeter.canSee(ticket.organizationId(), ticket.siteId())) {
            return false;
        }
        return ticket.requestedBy(perimeter.actor()) || assigned || widened(perimeter, ticket)
                || occupant(perimeter, ticket) || member(perimeter, ticket);
    }

    /**
     * Whether the reader sees the ticket only as a common ticket of their residence: no requester, photos or names
     * (minimization, ADR-037).
     */
    static boolean minimized(Perimeter perimeter, Ticket ticket, boolean assigned) {
        return !(ticket.requestedBy(perimeter.actor()) || assigned || widened(perimeter, ticket)
                || occupant(perimeter, ticket));
    }

    /** The permission that opens the ticket's visibility on its scope ({@code ticket:read} or {@code read-private}). */
    private static boolean widened(Perimeter perimeter, Ticket ticket) {
        Permission needed = ticket.visibility() == Visibility.COMMON ? Permission.TICKET_READ
                : Permission.TICKET_READ_PRIVATE;
        return perimeter.can(needed, ticket.organizationId(), ticket.siteId());
    }

    /** A private ticket of a unit the reader occupies. */
    private static boolean occupant(Perimeter perimeter, Ticket ticket) {
        return ticket.visibility() == Visibility.PRIVATE && ticket.location().unitId()
                .flatMap(unit -> perimeter.site(ticket.siteId()).map(a -> a.occupiedUnitIds().contains(unit)))
                .orElse(false);
    }

    /** A common ticket of a site where the reader is a member, in an allowed building (C7). */
    private static boolean member(Perimeter perimeter, Ticket ticket) {
        if (ticket.visibility() != Visibility.COMMON) {
            return false;
        }
        return perimeter.site(ticket.siteId()).filter(SiteAccess::member).isPresent()
                && ticket.location().buildingId().map(b -> perimeter.canSeeBuilding(ticket.organizationId(),
                ticket.siteId(), b)).orElse(true);
    }
}
