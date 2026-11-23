package com.almaraama.sites.application;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.sites.application.Views.ZoneView;
import com.almaraama.sites.domain.Country;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.ReferenceData;
import com.almaraama.sites.domain.Zone;
import com.almaraama.sites.domain.ZoneRepository;
import com.almaraama.sites.domain.ZoneStatus;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reads zones with their ancestors and checks the zone of an address (A8, A9). Ancestors are resolved from the
 * parents, in one query per level, so a list of addresses never costs a query per address.
 */
@Component
class ZoneBook {

    private final ZoneRepository zones;
    private final ReferenceData reference;

    /**
     * @param zones     the zone tree
     * @param reference countries, for the attachment level
     */
    ZoneBook(ZoneRepository zones, ReferenceData reference) {
        this.zones = zones;
        this.reference = reference;
    }

    /** One zone with its ancestors; {@code NOT_FOUND} when unknown. */
    ZoneView view(UUID zoneId) {
        return views(List.of(zoneId)).values().stream().findFirst()
                .orElseThrow(OrganizationService::notFound);
    }

    /** Several zones with their ancestors, by zone identifier; unknown identifiers are left out. */
    Map<UUID, ZoneView> views(Collection<UUID> zoneIds) {
        Map<UUID, Zone> known = new HashMap<>();
        Set<UUID> missing = new HashSet<>(zoneIds);
        while (!missing.isEmpty()) {
            List<Zone> found = zones.findAllById(missing);
            missing = new HashSet<>();
            for (Zone zone : found) {
                known.put(zone.id(), zone);
            }
            for (Zone zone : found) {
                zone.parentId().filter(parent -> !known.containsKey(parent)).ifPresent(missing::add);
            }
        }
        Map<UUID, ZoneView> views = new HashMap<>();
        for (UUID id : zoneIds) {
            Zone zone = known.get(id);
            if (zone != null) {
                views.put(id, new ZoneView(zone, ancestors(zone, known)));
            }
        }
        return views;
    }

    /**
     * Checks that an address of this organization may use the zone (A8, A9): {@code NOT_FOUND} for an unknown zone or
     * another organization's provisional zone, {@code ZONE_DEACTIVATED}, {@code ZONE_OUTSIDE_ORGANIZATION_COUNTRY},
     * {@code ZONE_NOT_ATTACHMENT_LEVEL}.
     */
    Zone requireAddressZone(Organization organization, UUID zoneId) {
        Zone zone = zones.findById(zoneId).orElseThrow(OrganizationService::notFound);
        if (zone.status() == ZoneStatus.PROVISIONAL
                && !zone.createdByOrganizationId().equals(Optional.of(organization.id()))) {
            throw OrganizationService.notFound();
        }
        if (zone.status() == ZoneStatus.DEACTIVATED) {
            throw rule("ZONE_DEACTIVATED");
        }
        if (!zone.countryCode().equals(organization.countryCode())) {
            throw rule("ZONE_OUTSIDE_ORGANIZATION_COUNTRY");
        }
        Country country = reference.findCountry(zone.countryCode()).orElseThrow(OrganizationService::notFound);
        if (zone.level() != country.attachmentLevel()) {
            throw rule("ZONE_NOT_ATTACHMENT_LEVEL");
        }
        return zone;
    }

    /** Whether the zone may be shown to an actor of these organizations. */
    static boolean visible(Zone zone, Set<OrganizationId> organizations, boolean platform) {
        return zone.visibleTo(organizations, platform);
    }

    /** The ancestors of a zone, from level 1 down to its parent. */
    private static List<Zone> ancestors(Zone zone, Map<UUID, Zone> known) {
        LinkedList<Zone> chain = new LinkedList<>();
        Optional<UUID> parent = zone.parentId();
        while (parent.isPresent() && known.containsKey(parent.get())) {
            Zone up = known.get(parent.get());
            chain.addFirst(up);
            parent = up.parentId();
        }
        return List.copyOf(chain);
    }

    /** A broken address rule (422). */
    private static BusinessException rule(String code) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, code);
    }
}
