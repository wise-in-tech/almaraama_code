package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.events.ZoneCreatedV1;
import com.almaraama.sites.application.Views.ZoneView;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reference data of the address forms (A8): countries and their parameters, the zone tree, and provisional zones
 * created by a promoteur when its country is not loaded (criterion 3).
 */
@Service
public class ReferenceService {

    private static final Logger LOG = LoggerFactory.getLogger(ReferenceService.class);

    private final ReferenceData reference;
    private final ZoneRepository zones;
    private final ZoneBook zoneBook;
    private final OrganizationRepository organizations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports of the reference data. */
    ReferenceService(ReferenceData reference, ZoneRepository zones, ZoneBook zoneBook,
                     OrganizationRepository organizations, ApplicationEventPublisher events, Clock clock) {
        this.reference = reference;
        this.zones = zones;
        this.zoneBook = zoneBook;
        this.organizations = organizations;
        this.events = events;
        this.clock = clock;
    }

    /** Every country of the reference data, with its parameters. */
    @Transactional(readOnly = true)
    public List<Country> countries() {
        return reference.countries();
    }

    /** The currency of a country (M5), for the organizations' answers; {@code NOT_FOUND} for an unknown country. */
    @Transactional(readOnly = true)
    public String currencyOf(String countryCode) {
        return reference.findCountry(countryCode).map(Country::currencyCode)
                .orElseThrow(OrganizationService::notFound);
    }

    /**
     * Zones of a country (A8): children of {@code parentId} or level 1, one {@code level}, or a name search; validated
     * zones and the provisional zones of the caller's organizations. {@code NOT_FOUND} for an unknown country.
     */
    @Transactional(readOnly = true)
    public PageResult<ZoneView> zones(Perimeter perimeter, String countryCode, UUID parentId, Integer level,
                                      String namePart, PageQuery page) {
        reference.findCountry(countryCode).orElseThrow(OrganizationService::notFound);
        String search = namePart == null || namePart.isBlank() ? null : namePart.strip();
        PageResult<Zone> found = zones.search(new ZoneRepository.Filter(countryCode,
                search == null ? parentId : null, level, search), page);
        Set<OrganizationId> mine = organizationsOf(perimeter);
        boolean platform = perimeter.canOnPlatform(Permission.ORGANIZATION_CREATE);
        List<Zone> visible = found.items().stream().filter(z -> ZoneBook.visible(z, mine, platform)).toList();
        Map<UUID, ZoneView> views = zoneBook.views(visible.stream().map(Zone::id).toList());
        return new PageResult<>(visible.stream().map(z -> views.get(z.id())).toList(), found.page(), found.size(),
                found.totalElements());
    }

    /** One zone with its ancestors; {@code NOT_FOUND} when unknown or not visible to the caller. */
    @Transactional(readOnly = true)
    public ZoneView zone(Perimeter perimeter, UUID zoneId) {
        ZoneView view = zoneBook.view(zoneId);
        if (!ZoneBook.visible(view.zone(), organizationsOf(perimeter),
                perimeter.canOnPlatform(Permission.ORGANIZATION_CREATE))) {
            throw OrganizationService.notFound();
        }
        return view;
    }

    /**
     * Creates a provisional zone at the attachment level of the organization's country (A8); needs {@code
     * zone:create-provisional} on the organization; {@code ZONE_PARENT_LEVEL_INVALID}, {@code
     * ZONE_OUTSIDE_ORGANIZATION_COUNTRY}, {@code ZONE_NAME_TAKEN}. Publishes {@code ZoneCreatedV1}.
     */
    @Transactional
    public ZoneView createProvisional(Perimeter perimeter, OrganizationId organizationId, Optional<UUID> parentId,
                                      LocalizedName name) {
        perimeter.require(Permission.ZONE_CREATE_PROVISIONAL, organizationId);
        LocalizedName complete = withFallback(name);
        Organization organization = organizations.findById(organizationId).orElseThrow(OrganizationService::notFound);
        Country country = reference.findCountry(organization.countryCode()).orElseThrow(OrganizationService::notFound);
        Optional<Zone> parent = parentId.map(id -> zones.findById(id).orElseThrow(OrganizationService::notFound));
        if (parent.isPresent()) {
            if (!parent.get().countryCode().equals(country.code())) {
                throw rule("ZONE_OUTSIDE_ORGANIZATION_COUNTRY");
            }
            if (parent.get().level() != country.attachmentLevel() - 1
                    || parent.get().status() == ZoneStatus.DEACTIVATED) {
                throw rule("ZONE_PARENT_LEVEL_INVALID");
            }
        }
        if (zones.nameTaken(country.code(), parentId.orElse(null), complete)) {
            throw new BusinessException("ZONE_NAME_TAKEN", ErrorCategory.CONFLICT, "Name already used");
        }
        Instant now = clock.instant();
        Zone zone = zones.save(Zone.provisional(country, parent, complete, organizationId, now));
        events.publishEvent(new ZoneCreatedV1(zone.id(), zone.countryCode(), zone.code(), zone.level(),
                organizationId, perimeter.actor(), now));
        LOG.debug("Provisional zone {} ({}) created by organization {}, path {}", zone.id(), zone.code(),
                organizationId.value(), zone.path());
        return zoneBook.view(zone.id());
    }

    /**
     * A name typed in one or more languages, completed for the others (M6: French, English, Arabic, in this order of
     * preference for the missing ones); {@code ZONE_NAME_INVALID} when no language is given or a name is over 100.
     */
    static LocalizedName withFallback(LocalizedName name) {
        String ar = clean(name.ar());
        String en = clean(name.en());
        String fr = clean(name.fr());
        String any = fr != null ? fr : en != null ? en : ar;
        if (any == null) {
            throw new BusinessException("ZONE_NAME_INVALID", ErrorCategory.VALIDATION, "A name is needed");
        }
        return new LocalizedName(ar != null ? ar : any, en != null ? en : any, fr != null ? fr : any);
    }

    /** A trimmed name, {@code null} when blank; {@code ZONE_NAME_INVALID} above 100 characters. */
    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > 100) {
            throw new BusinessException("ZONE_NAME_INVALID", ErrorCategory.VALIDATION, "100 characters at most");
        }
        return stripped;
    }

    /** The organizations the caller acts for: on the organization itself or through one of its sites. */
    private static Set<OrganizationId> organizationsOf(Perimeter perimeter) {
        Set<OrganizationId> mine = new HashSet<>(perimeter.organizations().keySet());
        perimeter.sites().values().forEach(site -> mine.add(site.organizationId()));
        return mine;
    }

    /** A broken zone rule (422). */
    private static BusinessException rule(String code) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, code);
    }
}
