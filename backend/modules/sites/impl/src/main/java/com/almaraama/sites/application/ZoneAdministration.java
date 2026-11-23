package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.events.ZoneDeactivatedV1;
import com.almaraama.sites.api.events.ZoneMergedV1;
import com.almaraama.sites.api.events.ZoneValidatedV1;
import com.almaraama.sites.application.Views.ZoneView;
import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.AddressRepository;
import com.almaraama.sites.domain.Country;
import com.almaraama.sites.domain.LocalizedName;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.OrganizationRepository;
import com.almaraama.sites.domain.ReferenceData;
import com.almaraama.sites.domain.Zone;
import com.almaraama.sites.domain.ZoneRepository;
import com.almaraama.sites.domain.ZoneStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Back-office of the zone tree (A8, M7, C12, ADR-035): the platform administrators list zones of every status, add
 * and correct validated zones, validate or merge the provisional zones created by the promoteurs, and deactivate
 * zones. The bulk loading of a country is done directly in the database by the architect. Nothing is deleted (M2).
 */
@Service
public class ZoneAdministration {

    private static final Logger LOG = LoggerFactory.getLogger(ZoneAdministration.class);

    /** A zone for the back-office: with ancestors, active address count, creating organization and merge target. */
    public record AdminZoneView(ZoneView zone, long addressCount, Optional<Organization> createdBy,
                                Optional<Zone> mergedInto) {
    }

    private final ZoneRepository zones;
    private final AddressRepository addresses;
    private final OrganizationRepository organizations;
    private final ReferenceData reference;
    private final ZoneBook zoneBook;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports of the zone back-office. */
    ZoneAdministration(ZoneRepository zones, AddressRepository addresses, OrganizationRepository organizations,
                       ReferenceData reference, ZoneBook zoneBook, ApplicationEventPublisher events, Clock clock) {
        this.zones = zones;
        this.addresses = addresses;
        this.organizations = organizations;
        this.reference = reference;
        this.zoneBook = zoneBook;
        this.events = events;
        this.clock = clock;
    }

    /** Zones of a country in every status; needs {@code reference-data:read} on the platform. */
    @Transactional(readOnly = true)
    public PageResult<AdminZoneView> search(Perimeter perimeter, ZoneRepository.AdminFilter filter, PageQuery page) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_READ);
        PageResult<Zone> found = zones.adminSearch(filter, page);
        return views(found);
    }

    /**
     * A8, M7: adds a validated zone; needs {@code reference-data:create}. {@code COUNTRY_UNKNOWN}, {@code
     * ZONE_PARENT_INVALID} (deactivated or of another country), {@code ZONE_LEVEL_INVALID}, {@code ZONE_CODE_INVALID},
     * {@code ZONE_CODE_TAKEN}, {@code ZONE_NAME_TAKEN}, {@code NOT_FOUND} for an unknown parent.
     */
    @Transactional
    public AdminZoneView create(Perimeter perimeter, String countryCode, Optional<UUID> parentId, String code,
                                LocalizedName name) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_CREATE);
        Country country = reference.findCountry(countryCode).orElseThrow(() -> new BusinessException(
                "COUNTRY_UNKNOWN", ErrorCategory.RULE_VIOLATION, "Country not loaded"));
        Optional<Zone> parent = parentId.map(this::load);
        if (parent.isPresent() && (parent.get().status() != ZoneStatus.VALIDATED
                || !parent.get().countryCode().equals(country.code()))) {
            throw rule("ZONE_PARENT_INVALID");
        }
        LocalizedName complete = ReferenceService.withFallback(name);
        requireFreeCode(country.code(), code, null);
        if (zones.nameTaken(country.code(), parentId.orElse(null), complete)) {
            throw conflict("ZONE_NAME_TAKEN");
        }
        Zone zone = zones.save(Zone.validated(country, parent, code, complete, clock.instant()));
        LOG.debug("Zone {} ({}) added at level {}", zone.id(), zone.code(), zone.level());
        return view(zone);
    }

    /**
     * Corrects the code and names of a zone; needs {@code reference-data:update}; the paths of its descendants follow.
     * {@code VERSION_CONFLICT}, {@code ZONE_CODE_INVALID}, {@code ZONE_CODE_TAKEN}.
     */
    @Transactional
    public AdminZoneView correct(Perimeter perimeter, UUID zoneId, String code, LocalizedName name, long version) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_UPDATE);
        Zone zone = load(zoneId);
        Versions.check(version, zone.version());
        requireFreeCode(zone.countryCode(), code, zone.id());
        Instant now = clock.instant();
        zone.correct(code, ReferenceService.withFallback(name), now);
        zones.save(zone);
        moveDescendants(zone, now);
        return view(zone);
    }

    /**
     * A8, criterion 3: validates a provisional zone with its final code and, when given, its parent (a validated
     * zone of the level just above); its addresses are kept. Needs {@code reference-data:validate-provisional}.
     * {@code ZONE_NOT_PROVISIONAL}, {@code ZONE_PARENT_LEVEL_INVALID}, {@code ZONE_CODE_TAKEN}, {@code
     * VERSION_CONFLICT}. Publishes {@code ZoneValidatedV1}.
     */
    @Transactional
    public AdminZoneView validate(Perimeter perimeter, UUID zoneId, String code, Optional<UUID> parentId,
                                  Optional<LocalizedName> name, long version) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_VALIDATE_PROVISIONAL);
        Zone zone = load(zoneId);
        Versions.check(version, zone.version());
        Optional<Zone> parent = parentId.map(this::load);
        if (parent.isPresent() && (parent.get().status() != ZoneStatus.VALIDATED
                || !parent.get().countryCode().equals(zone.countryCode()) || parent.get().level() != zone.level() - 1)) {
            throw rule("ZONE_PARENT_LEVEL_INVALID");
        }
        requireFreeCode(zone.countryCode(), code, zone.id());
        Instant now = clock.instant();
        zone.validate(code, parent, name.map(ReferenceService::withFallback), now);
        zones.save(zone);
        events.publishEvent(new ZoneValidatedV1(zone.id(), zone.countryCode(), zone.code(), perimeter.actor(), now));
        LOG.info("Provisional zone {} validated as {}", zone.id(), zone.code());
        return view(zone);
    }

    /**
     * A8: merges a provisional zone into a validated one: each current address moves to the target (the old row is
     * kept, M2); the provisional zone becomes deactivated with {@code mergedInto}. Needs {@code
     * reference-data:validate-provisional}. {@code ZONE_NOT_PROVISIONAL}, {@code ZONE_MERGE_TARGET_INVALID}, {@code
     * VERSION_CONFLICT}. Publishes {@code ZoneMergedV1}.
     */
    @Transactional
    public AdminZoneView merge(Perimeter perimeter, UUID zoneId, UUID targetZoneId, long version) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_VALIDATE_PROVISIONAL);
        Zone zone = load(zoneId);
        Versions.check(version, zone.version());
        Zone target = load(targetZoneId);
        Instant now = clock.instant();
        zone.mergeInto(target, now);
        List<Address> moving = addresses.currentInZone(zone.id());
        for (Address current : moving) {
            current.replace(now);
            addresses.saveReplaced(current);
            addresses.save(Address.movedToZone(current, target.id(), now));
        }
        zones.save(zone);
        events.publishEvent(new ZoneMergedV1(zone.id(), target.id(), moving.size(), perimeter.actor(), now));
        LOG.info("Provisional zone {} merged into {} ({} addresses moved)", zone.id(), target.id(), moving.size());
        return view(zone);
    }

    /**
     * A8, M2: deactivates a zone (kept with its addresses, no longer offered); needs {@code reference-data:archive}.
     * {@code ZONE_HAS_ACTIVE_CHILDREN}, {@code ZONE_ALREADY_DEACTIVATED}, {@code VERSION_CONFLICT}. Publishes {@code
     * ZoneDeactivatedV1}.
     */
    @Transactional
    public AdminZoneView deactivate(Perimeter perimeter, UUID zoneId, long version) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_ARCHIVE);
        Zone zone = load(zoneId);
        Versions.check(version, zone.version());
        if (zones.children(zone.id()).stream().anyMatch(child -> child.status() != ZoneStatus.DEACTIVATED)) {
            throw rule("ZONE_HAS_ACTIVE_CHILDREN");
        }
        Instant now = clock.instant();
        zone.deactivate(now);
        zones.save(zone);
        events.publishEvent(new ZoneDeactivatedV1(zone.id(), zone.countryCode(), zone.code(), perimeter.actor(),
                now));
        return view(zone);
    }

    /** Recomputes the paths of every descendant after a zone's code changed (breadth first). */
    private void moveDescendants(Zone root, Instant now) {
        Deque<Zone> pending = new ArrayDeque<>(List.of(root));
        while (!pending.isEmpty()) {
            Zone parent = pending.poll();
            for (Zone child : zones.children(parent.id())) {
                child.followParent(parent, now);
                zones.save(child);
                pending.add(child);
            }
        }
    }

    /** {@code ZONE_CODE_TAKEN} when another zone of the country has the code. */
    private void requireFreeCode(String countryCode, String code, UUID except) {
        if (code != null && zones.codeTaken(countryCode, code.strip(), except)) {
            throw conflict("ZONE_CODE_TAKEN");
        }
    }

    /** One zone for the back-office. */
    private AdminZoneView view(Zone zone) {
        return views(new PageResult<>(List.of(zone), 0, 1, 1)).items().getFirst();
    }

    /** A page of zones for the back-office, with their address counts, creators and targets in a few queries. */
    private PageResult<AdminZoneView> views(PageResult<Zone> found) {
        List<UUID> ids = found.items().stream().map(Zone::id).toList();
        Map<UUID, ZoneView> withAncestors = zoneBook.views(ids);
        Map<UUID, Long> counts = addresses.currentCountByZone(ids);
        Map<OrganizationId, Organization> creators = organizations.findAllById(found.items().stream()
                        .flatMap(z -> z.createdByOrganizationId().stream()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Organization::id, Function.identity()));
        Map<UUID, Zone> targets = zones.findAllById(found.items().stream().flatMap(z -> z.mergedInto().stream())
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Zone::id, Function.identity()));
        return found.map(z -> new AdminZoneView(withAncestors.getOrDefault(z.id(), new ZoneView(z, List.of())),
                counts.getOrDefault(z.id(), 0L), z.createdByOrganizationId().map(creators::get),
                z.mergedInto().map(targets::get)));
    }

    /** The zone, or {@code NOT_FOUND}. */
    private Zone load(UUID id) {
        return zones.findById(id).orElseThrow(OrganizationService::notFound);
    }

    /** A broken rule of the tree (422). */
    private static BusinessException rule(String code) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, code);
    }

    /** A conflict with another zone (409). */
    private static BusinessException conflict(String code) {
        return new BusinessException(code, ErrorCategory.CONFLICT, code);
    }
}
