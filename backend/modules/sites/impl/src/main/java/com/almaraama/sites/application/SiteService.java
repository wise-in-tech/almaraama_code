package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.events.SiteCreatedV1;
import com.almaraama.sites.api.events.SiteUpdatedV1;
import com.almaraama.sites.application.Views.AddressView;
import com.almaraama.sites.application.Views.SiteView;
import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.AddressOwnerType;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.OrganizationRepository;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.SiteLifecycleStatus;
import com.almaraama.sites.domain.SiteRepository;
import com.almaraama.sites.domain.SiteType;
import com.almaraama.sites.domain.ZoneRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sites of an organization and their address (A1, A5, A8, A9). */
@Service
public class SiteService {

    private static final Logger LOG = LoggerFactory.getLogger(SiteService.class);

    /** What a person enters to create a site. */
    public record CreateSite(SiteType type, String name, Address.Spec address, SiteLifecycleStatus lifecycleStatus) {
    }

    /** What a person enters to change a site, with the version they read. */
    public record UpdateSite(String name, Address.Spec address, SiteLifecycleStatus lifecycleStatus, boolean active,
                             long version) {
    }

    /**
     * Criteria of the site list (criterion 3): {@code zoneId} at any level of the tree (a wilaya gives the sites of
     * all its communes), part of the name, and whether archived sites are included.
     */
    public record SiteFilter(UUID zoneId, String namePart, boolean includeInactive) {
    }

    private final SiteRepository sites;
    private final OrganizationRepository organizations;
    private final ZoneRepository zones;
    private final AddressBook addresses;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the site use cases. */
    SiteService(SiteRepository sites, OrganizationRepository organizations, ZoneRepository zones,
                AddressBook addresses, ApplicationEventPublisher events, Clock clock) {
        this.sites = sites;
        this.organizations = organizations;
        this.zones = zones;
        this.addresses = addresses;
        this.events = events;
        this.clock = clock;
    }

    /**
     * A role on the organization shows all its sites; otherwise only the sites of the caller's perimeter. Filtered by
     * zone at any level, by name and by status; {@code NOT_FOUND} for an unknown zone.
     */
    @Transactional(readOnly = true)
    public PageResult<SiteView> list(Perimeter perimeter, OrganizationId organizationId, SiteFilter filter,
                                     PageQuery page) {
        perimeter.requireVisible(organizationId);
        Organization organization = organization(organizationId);
        List<SiteId> only = perimeter.seesWholeOrganization(organizationId) ? null
                : perimeter.sites().entrySet().stream()
                        .filter(entry -> entry.getValue().organizationId().equals(organizationId))
                        .map(Map.Entry::getKey).toList();
        Optional<String> zonePath = Optional.ofNullable(filter.zoneId())
                .map(id -> zones.findById(id).orElseThrow(OrganizationService::notFound).path());
        PageResult<Site> found = sites.page(organizationId, only, new SiteRepository.Filter(zonePath.orElse(null),
                filter.namePart(), filter.includeInactive()), page);
        Map<UUID, AddressView> views = addresses.current(AddressOwnerType.SITE,
                found.items().stream().map(site -> site.id().value()).toList());
        LOG.debug("Sites of organization {} listed: {} found (zone filter {})", organizationId.value(),
                found.totalElements(), zonePath.orElse("none"));
        return found.map(site -> new SiteView(site, organization.countryCode(), views.get(site.id().value())));
    }

    /**
     * Creates a residential site with its address (A1, A9); needs {@code site:create}; {@code
     * SITE_TYPE_NOT_AVAILABLE}, {@code ORGANIZATION_SINGLE_SITE} for a second site of a {@code RESIDENCE}, and the
     * address codes of {@link ZoneBook#requireAddressZone}. Publishes {@code SiteCreatedV1}.
     */
    @Transactional
    public SiteView create(Perimeter perimeter, OrganizationId organizationId, CreateSite command) {
        perimeter.require(Permission.SITE_CREATE, organizationId);
        Organization organization = organization(organizationId);
        if (organization.singleSite() && sites.countOf(organizationId) > 0) {
            throw new BusinessException("ORGANIZATION_SINGLE_SITE", ErrorCategory.RULE_VIOLATION,
                    "A RESIDENCE organization has one site only");
        }
        Instant now = clock.instant();
        Site site = sites.save(Site.create(organizationId, command.type(), command.name(),
                command.lifecycleStatus(), now));
        addresses.set(organization, site.id(), AddressOwnerType.SITE, site.id().value(), command.address(), now);
        events.publishEvent(new SiteCreatedV1(site.id(), organizationId, site.name(), perimeter.actor(), now));
        LOG.debug("Site {} created in organization {}", site.id().value(), organizationId.value());
        return view(site, organization);
    }

    /** One visible site with its address. */
    @Transactional(readOnly = true)
    public SiteView get(Perimeter perimeter, SiteId siteId) {
        Site site = visible(perimeter, siteId);
        return view(site, organization(site.organizationId()));
    }

    /**
     * Changes, archives or reactivates a site and replaces its address if it changed; needs {@code site:update}, and
     * {@code site:archive} to change {@code active}; {@code VERSION_CONFLICT}. The buildings keep their own address
     * (A9: no live inheritance). Publishes {@code SiteUpdatedV1}.
     */
    @Transactional
    public SiteView update(Perimeter perimeter, SiteId siteId, UpdateSite command) {
        Site site = visible(perimeter, siteId);
        perimeter.require(Permission.SITE_UPDATE, site.organizationId(), siteId);
        Archiving.requireIfChanged(perimeter, Permission.SITE_ARCHIVE, site.active(), command.active(),
                site.organizationId(), siteId);
        Versions.check(command.version(), site.version());
        Organization organization = organization(site.organizationId());
        Instant now = clock.instant();
        site.update(command.name(), command.lifecycleStatus(), command.active(), now);
        sites.save(site);
        addresses.set(organization, siteId, AddressOwnerType.SITE, siteId.value(), command.address(), now);
        events.publishEvent(new SiteUpdatedV1(siteId, site.organizationId(), site.name(), site.active(),
                perimeter.actor(), now));
        return view(site, organization);
    }

    /** Loads a site the caller may see; {@code NOT_FOUND} otherwise (M3). */
    Site visible(Perimeter perimeter, SiteId siteId) {
        Site site = sites.findById(siteId).orElseThrow(OrganizationService::notFound);
        perimeter.requireVisible(site.organizationId(), siteId);
        return site;
    }

    /** The organization of a site, which always exists. */
    Organization organization(OrganizationId organizationId) {
        return organizations.findById(organizationId).orElseThrow(OrganizationService::notFound);
    }

    /** A site with its current address. */
    private SiteView view(Site site, Organization organization) {
        return new SiteView(site, organization.countryCode(), addresses.current(AddressOwnerType.SITE,
                        site.id().value())
                .orElseThrow(() -> new IllegalStateException("Site without address: " + site.id())));
    }
}
