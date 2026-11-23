package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.events.BuildingCreatedV1;
import com.almaraama.sites.application.Views.AddressView;
import com.almaraama.sites.application.Views.BuildingView;
import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.AddressOwnerType;
import com.almaraama.sites.domain.Building;
import com.almaraama.sites.domain.BuildingRepository;
import com.almaraama.sites.domain.BuildingUse;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.UnitRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buildings of a site (A2, A5) and their address (A9): a new building receives a copy of the site's address, or its
 * own when given; the copy is independent from then on (decision of 20 November 2026).
 */
@Service
public class BuildingService {

    /** What a person enters for a building; at creation the address is optional (copy of the site's when absent). */
    public record BuildingCommand(String name, BuildingUse use, int floorCount, Optional<Address.Spec> address) {
    }

    private final BuildingRepository buildings;
    private final UnitRepository units;
    private final SiteService sites;
    private final AddressBook addresses;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the building use cases. */
    BuildingService(BuildingRepository buildings, UnitRepository units, SiteService sites, AddressBook addresses,
                    ApplicationEventPublisher events, Clock clock) {
        this.buildings = buildings;
        this.units = units;
        this.sites = sites;
        this.addresses = addresses;
        this.events = events;
        this.clock = clock;
    }

    /** The buildings of a visible site with their address; a restricted resident sees only theirs (C7, criterion 18). */
    @Transactional(readOnly = true)
    public List<BuildingView> list(Perimeter perimeter, SiteId siteId, boolean includeInactive) {
        Site site = sites.visible(perimeter, siteId);
        List<Building> visible = buildings.ofSite(siteId, includeInactive).stream()
                .filter(b -> perimeter.canSeeBuilding(site.organizationId(), siteId, b.id()))
                .toList();
        Map<UUID, AddressView> own = addresses.current(AddressOwnerType.BUILDING,
                visible.stream().map(b -> b.id().value()).toList());
        return visible.stream().map(b -> new BuildingView(b, own.get(b.id().value()))).toList();
    }

    /**
     * Creates a building; needs {@code building:create}; {@code BUILDING_NAME_TAKEN}, {@code
     * BUILDING_USE_NOT_AVAILABLE}, and the address codes when an address is given. Without address, the building
     * receives a copy of the site's address (A9). Publishes {@code BuildingCreatedV1}.
     */
    @Transactional
    public BuildingView create(Perimeter perimeter, SiteId siteId, BuildingCommand command) {
        Site site = sites.visible(perimeter, siteId);
        perimeter.require(Permission.BUILDING_CREATE, site.organizationId(), siteId);
        requireFreeName(siteId, command.name(), null);
        Instant now = clock.instant();
        Building building = buildings.save(Building.create(site, command.name(), command.use(),
                command.floorCount(), now));
        if (command.address().isPresent()) {
            addresses.set(sites.organization(site.organizationId()), siteId, AddressOwnerType.BUILDING,
                    building.id().value(), command.address().get(), now);
        } else {
            addresses.copySiteAddressTo(siteId, building.id().value(), now);
        }
        events.publishEvent(new BuildingCreatedV1(building.id(), siteId, site.organizationId(), building.name(),
                perimeter.actor(), now));
        return view(building);
    }

    /**
     * Changes or archives a building and replaces its address when it changed; needs {@code building:update}, and
     * {@code building:archive} to change {@code active}; {@code VERSION_CONFLICT}, {@code BUILDING_HAS_ACTIVE_UNITS}
     * when archiving a building that holds units.
     */
    @Transactional
    public BuildingView update(Perimeter perimeter, BuildingId buildingId, BuildingCommand command, boolean active,
                               long version) {
        Building building = buildings.findById(buildingId).orElseThrow(OrganizationService::notFound);
        Site site = sites.visible(perimeter, building.siteId());
        perimeter.require(Permission.BUILDING_UPDATE, site.organizationId(), site.id());
        Archiving.requireIfChanged(perimeter, Permission.BUILDING_ARCHIVE, building.active(), active,
                site.organizationId(), site.id());
        Versions.check(version, building.version());
        requireFreeName(site.id(), command.name(), buildingId);
        if (!active && building.active() && units.hasActiveUnitsIn(buildingId)) {
            throw new BusinessException("BUILDING_HAS_ACTIVE_UNITS", ErrorCategory.RULE_VIOLATION,
                    "Deactivate or move its units first");
        }
        Instant now = clock.instant();
        building.update(command.name(), command.use(), command.floorCount(), active, now);
        buildings.save(building);
        Address.Spec address = command.address().orElseThrow(() -> new BusinessException("VALIDATION_FAILED",
                ErrorCategory.VALIDATION, "The address is always sent on update"));
        addresses.set(sites.organization(site.organizationId()), site.id(), AddressOwnerType.BUILDING,
                buildingId.value(), address, now);
        return view(building);
    }

    /** {@code BUILDING_NAME_TAKEN} when another building of the site has this name. */
    private void requireFreeName(SiteId siteId, String name, BuildingId except) {
        if (name != null && buildings.nameTaken(siteId, name.strip(), except)) {
            throw new BusinessException("BUILDING_NAME_TAKEN", ErrorCategory.CONFLICT, "Name already used");
        }
    }

    /** A building with its current address, which every building has. */
    private BuildingView view(Building building) {
        return new BuildingView(building, addresses.current(AddressOwnerType.BUILDING, building.id().value())
                .orElseThrow(() -> new IllegalStateException("Building without address: " + building.id())));
    }
}
