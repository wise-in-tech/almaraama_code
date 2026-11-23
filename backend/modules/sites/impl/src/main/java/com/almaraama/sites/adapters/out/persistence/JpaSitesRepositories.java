package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.AddressOwnerType;
import com.almaraama.sites.domain.AddressRepository;
import com.almaraama.sites.domain.Building;
import com.almaraama.sites.domain.BuildingRepository;
import com.almaraama.sites.domain.Country;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.OrganizationRepository;
import com.almaraama.sites.domain.OutdoorArea;
import com.almaraama.sites.domain.OutdoorAreaRepository;
import com.almaraama.sites.domain.LocalizedName;
import com.almaraama.sites.domain.ReferenceData;
import com.almaraama.sites.domain.UnitCategory;
import com.almaraama.sites.domain.UnitCategoryRepository;
import com.almaraama.sites.domain.Zone;
import com.almaraama.sites.domain.ZoneRepository;
import com.almaraama.sites.api.UnitType;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.SiteRepository;
import com.almaraama.sites.domain.Unit;
import com.almaraama.sites.domain.UnitRepository;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Adapters from the domain's out ports to Spring Data JPA, in schema {@code sites} only (CF-3). */
final class JpaSitesRepositories {

    private static final UUID NONE = new UUID(0, 0);

    /** Holder of the adapter classes only. */
    private JpaSitesRepositories() {
    }

    /** Spring Data paging from the domain's page query. */
    static PageRequest pageable(PageQuery page) {
        return PageRequest.of(page.page(), page.size());
    }

    /** The domain's page result from a Spring Data page. */
    static <T> PageResult<T> result(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Raw UUIDs of typed identifiers, for queries. */
    private static List<UUID> uuids(Collection<?> ids, Function<Object, UUID> value) {
        return ids.stream().map(value).toList();
    }

    /** {@link OrganizationRepository} on the {@code sites.organization} table. */
    @Component
    static class Organizations implements OrganizationRepository {
        private final SpringDataOrganizations organizations;
        private final SpringDataSites sites;
        private final JpaStructureSearch structureSearch;

        /**
         * @param organizations   Spring Data repository of the organization table
         * @param sites           counts the sites of the organizations
         * @param structureSearch back-office search
         */
        Organizations(SpringDataOrganizations organizations, SpringDataSites sites,
                      JpaStructureSearch structureSearch) {
            this.organizations = organizations;
            this.sites = sites;
            this.structureSearch = structureSearch;
        }

        /** Loads an organization. */
        @Override
        public Optional<Organization> findById(OrganizationId id) {
            return organizations.findById(id.value());
        }

        /** Loads several organizations; an empty input gives an empty list without querying. */
        @Override
        public List<Organization> findAllById(Collection<OrganizationId> ids) {
            return ids.isEmpty() ? List.of() : organizations.findByIdIn(uuids(ids, id -> ((OrganizationId) id).value()));
        }

        /** All organizations ordered by name (our team). */
        @Override
        public PageResult<Organization> page(PageQuery page) {
            return result(organizations.findAllByOrderByNameAsc(pageable(page)));
        }

        /** The given organizations ordered by name. */
        @Override
        public PageResult<Organization> page(Collection<OrganizationId> ids, PageQuery page) {
            if (ids.isEmpty()) {
                return new PageResult<>(List.of(), page.page(), page.size(), 0);
            }
            return result(organizations.findByIdInOrderByNameAsc(
                    uuids(ids, id -> ((OrganizationId) id).value()), pageable(page)));
        }

        /** One page of every organization matching the back-office criteria. */
        @Override
        public PageResult<Organization> search(Search criteria, PageQuery page) {
            return structureSearch.organizations(criteria, page);
        }

        /** Number of sites per organization, in one query. */
        @Override
        public Map<OrganizationId, Long> siteCounts(Collection<OrganizationId> ids) {
            if (ids.isEmpty()) {
                return Map.of();
            }
            return sites.countByOrganizations(uuids(ids, id -> ((OrganizationId) id).value())).stream()
                    .collect(Collectors.toMap(row -> new OrganizationId((UUID) row[0]), row -> (Long) row[1]));
        }

        /** Inserts or updates an organization. */
        @Override
        public Organization save(Organization organization) {
            return organizations.save(organization);
        }
    }

    /** {@link SiteRepository} on the {@code sites.site} table. */
    @Component
    static class Sites implements SiteRepository {
        private final SpringDataSites sites;
        private final JpaStructureSearch search;

        /**
         * @param sites  Spring Data repository of the site table
         * @param search criteria searches (zone filter)
         */
        Sites(SpringDataSites sites, JpaStructureSearch search) {
            this.sites = sites;
            this.search = search;
        }

        /** Loads a site. */
        @Override
        public Optional<Site> findById(SiteId id) {
            return sites.findById(id.value());
        }

        /** Loads several sites; an empty input gives an empty list without querying. */
        @Override
        public List<Site> findAllById(Collection<SiteId> ids) {
            return ids.isEmpty() ? List.of() : sites.findByIdIn(uuids(ids, id -> ((SiteId) id).value()));
        }

        /** Sites of an organization matching the filter, by name; {@code onlyIds == null} means all of them. */
        @Override
        public PageResult<Site> page(OrganizationId organizationId, Collection<SiteId> onlyIds, Filter filter,
                                     PageQuery page) {
            return search.sites(organizationId, onlyIds == null ? null : uuids(onlyIds, id -> ((SiteId) id).value()),
                    filter, page);
        }

        /** Number of sites of an organization, archived ones included. */
        @Override
        public long countOf(OrganizationId organizationId) {
            return sites.countByOrganizationId(organizationId.value());
        }

        /** Inserts or updates a site. */
        @Override
        public Site save(Site site) {
            return sites.save(site);
        }
    }

    /** {@link BuildingRepository} on the {@code sites.building} table. */
    @Component
    static class Buildings implements BuildingRepository {
        private final SpringDataBuildings buildings;

        /** @param buildings Spring Data repository of the building table */
        Buildings(SpringDataBuildings buildings) {
            this.buildings = buildings;
        }

        /** Loads a building. */
        @Override
        public Optional<Building> findById(BuildingId id) {
            return buildings.findById(id.value());
        }

        /** Buildings of a site ordered by name. */
        @Override
        public List<Building> ofSite(SiteId siteId, boolean includeInactive) {
            return includeInactive
                    ? buildings.findBySiteIdOrderByNameAsc(siteId.value())
                    : buildings.findBySiteIdAndActiveTrueOrderByNameAsc(siteId.value());
        }

        /** Whether another building of the site has this name, ignoring case. */
        @Override
        public boolean nameTaken(SiteId siteId, String name, BuildingId except) {
            return buildings.nameTaken(siteId.value(), name, except == null ? NONE : except.value());
        }

        /** Inserts or updates a building. */
        @Override
        public Building save(Building building) {
            return buildings.save(building);
        }
    }

    /** {@link OutdoorAreaRepository} on the {@code sites.outdoor_area} table. */
    @Component
    static class OutdoorAreas implements OutdoorAreaRepository {
        private final SpringDataOutdoorAreas areas;

        /** @param areas Spring Data repository of the outdoor area table */
        OutdoorAreas(SpringDataOutdoorAreas areas) {
            this.areas = areas;
        }

        /** Loads an outdoor area. */
        @Override
        public Optional<OutdoorArea> findById(UUID id) {
            return areas.findById(id);
        }

        /** Outdoor areas of a site ordered by name. */
        @Override
        public List<OutdoorArea> ofSite(SiteId siteId, boolean includeInactive) {
            return includeInactive
                    ? areas.findBySiteIdOrderByNameAsc(siteId.value())
                    : areas.findBySiteIdAndActiveTrueOrderByNameAsc(siteId.value());
        }

        /** Whether another outdoor area of the site has this name, ignoring case. */
        @Override
        public boolean nameTaken(SiteId siteId, String name, UUID except) {
            return areas.nameTaken(siteId.value(), name, except == null ? NONE : except);
        }

        /** Inserts or updates an outdoor area. */
        @Override
        public OutdoorArea save(OutdoorArea area) {
            return areas.save(area);
        }
    }

    /** {@link UnitRepository} on the {@code sites.unit} table. */
    @Component
    static class Units implements UnitRepository {
        private final SpringDataUnits units;
        private final JpaUnitSearch search;

        /**
         * @param units Spring Data repository of the unit table
         * @param search searches with optional criteria
         */
        Units(SpringDataUnits units, JpaUnitSearch search) {
            this.units = units;
            this.search = search;
        }

        /** Loads a unit. */
        @Override
        public Optional<Unit> findById(UnitId id) {
            return units.findById(id.value());
        }

        /** Loads several units; an empty input gives an empty list without querying. */
        @Override
        public List<Unit> findAllById(Collection<UnitId> ids) {
            return ids.isEmpty() ? List.of() : units.findByIdIn(uuids(ids, id -> ((UnitId) id).value()));
        }

        /** Units of a site matching the filter, ordered by reference. */
        @Override
        public PageResult<Unit> search(SiteId siteId, Filter filter, PageQuery page) {
            return search.search(siteId, filter, page);
        }

        /** The unit of the site with this reference, ignoring case. */
        @Override
        public Optional<Unit> findByReference(SiteId siteId, String reference) {
            return units.findByReference(siteId.value(), reference.strip());
        }

        /** Every unit of the site, active or not. */
        @Override
        public List<Unit> ofSite(SiteId siteId) {
            return units.findBySiteId(siteId.value());
        }

        /** Whether the building still holds active units. */
        @Override
        public boolean hasActiveUnitsIn(BuildingId buildingId) {
            return units.existsByBuildingIdAndActiveTrue(buildingId.value());
        }

        /** Whether the outdoor area still holds active units. */
        @Override
        public boolean hasActiveUnitsInOutdoorArea(UUID outdoorAreaId) {
            return units.existsByOutdoorAreaIdAndActiveTrue(outdoorAreaId);
        }

        /** Inserts or updates a unit. */
        @Override
        public Unit save(Unit unit) {
            return units.save(unit);
        }

        /** Inserts several units in the current transaction (import). */
        @Override
        public List<Unit> saveAll(List<Unit> list) {
            return units.saveAll(list);
        }
    }

    /** {@link AddressRepository} on the {@code sites.address} table. */
    @Component
    static class Addresses implements AddressRepository {
        private final SpringDataAddresses addresses;

        /** @param addresses Spring Data repository of the address table */
        Addresses(SpringDataAddresses addresses) {
            this.addresses = addresses;
        }

        /** The owner's current address, ignoring replaced ones. */
        @Override
        public Optional<Address> current(AddressOwnerType ownerType, UUID ownerId) {
            return addresses.findByOwnerTypeAndOwnerIdAndReplacedAtIsNull(ownerType, ownerId);
        }

        /** The current address of each owner that has one, by owner identifier. */
        @Override
        public Map<UUID, Address> current(AddressOwnerType ownerType, Collection<UUID> ownerIds) {
            if (ownerIds.isEmpty()) {
                return Map.of();
            }
            return addresses.findByOwnerTypeAndOwnerIdInAndReplacedAtIsNull(ownerType, ownerIds).stream()
                    .collect(Collectors.toMap(Address::ownerId, Function.identity()));
        }

        /** The current addresses of a zone. */
        @Override
        public List<Address> currentInZone(UUID zoneId) {
            return addresses.findByZoneIdAndReplacedAtIsNull(zoneId);
        }

        /** Number of current addresses per zone, in one query. */
        @Override
        public Map<UUID, Long> currentCountByZone(Collection<UUID> zoneIds) {
            if (zoneIds.isEmpty()) {
                return Map.of();
            }
            return addresses.countCurrentByZone(zoneIds).stream()
                    .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        }

        /** Stores a replaced address and flushes it before the new one is inserted. */
        @Override
        public Address saveReplaced(Address address) {
            return addresses.saveAndFlush(address);
        }

        /** Inserts or updates an address. */
        @Override
        public Address save(Address address) {
            return addresses.save(address);
        }
    }

    /** {@link ReferenceData} on the country tables, read only. */
    @Component
    static class References implements ReferenceData {
        private final SpringDataCountries countries;

        /** @param countries Spring Data repository of the countries */
        References(SpringDataCountries countries) {
            this.countries = countries;
        }

        /** Every country of the reference data, ordered by code. */
        @Override
        public List<Country> countries() {
            return countries.findAllByOrderByCodeAsc();
        }

        /** A country by ISO code. */
        @Override
        public Optional<Country> findCountry(String code) {
            return countries.findById(code);
        }
    }

    /** {@link ZoneRepository} on the {@code sites.zone} table. */
    @Component
    static class Zones implements ZoneRepository {
        private final SpringDataZones zones;
        private final JpaStructureSearch search;

        /**
         * @param zones  Spring Data repository of the zones
         * @param search criteria searches
         */
        Zones(SpringDataZones zones, JpaStructureSearch search) {
            this.zones = zones;
            this.search = search;
        }

        /** Loads a zone. */
        @Override
        public Optional<Zone> findById(UUID id) {
            return zones.findById(id);
        }

        /** Loads several zones; an empty input gives an empty list without querying. */
        @Override
        public List<Zone> findAllById(Collection<UUID> ids) {
            return ids.isEmpty() ? List.of() : zones.findByIdIn(ids);
        }

        /** One page of zones matching the filter. */
        @Override
        public PageResult<Zone> search(Filter filter, PageQuery page) {
            return search.zones(filter, page);
        }

        /** One page of zones of every status for the back-office. */
        @Override
        public PageResult<Zone> adminSearch(AdminFilter filter, PageQuery page) {
            return search.adminZones(filter, page);
        }

        /** The children of a zone. */
        @Override
        public List<Zone> children(UUID parentId) {
            return zones.findByParentId(parentId);
        }

        /** Whether another zone of the country has this code. */
        @Override
        public boolean codeTaken(String countryCode, String code, UUID except) {
            return zones.existsByCountryCodeAndCodeAndIdNot(countryCode, code,
                    except == null ? new UUID(0, 0) : except);
        }

        /** Whether the name is already used under the same parent. */
        @Override
        public boolean nameTaken(String countryCode, UUID parentId, LocalizedName name) {
            return search.zoneNameTaken(countryCode, parentId, name);
        }

        /** Inserts or updates a zone. */
        @Override
        public Zone save(Zone zone) {
            return zones.save(zone);
        }
    }

    /** {@link UnitCategoryRepository} on the {@code sites.unit_category} table. */
    @Component
    static class UnitCategories implements UnitCategoryRepository {
        private final SpringDataUnitCategories categories;
        private final JpaStructureSearch search;

        /**
         * @param categories Spring Data repository of the categories
         * @param search     criteria searches
         */
        UnitCategories(SpringDataUnitCategories categories, JpaStructureSearch search) {
            this.categories = categories;
            this.search = search;
        }

        /** Loads a category. */
        @Override
        public Optional<UnitCategory> findById(UUID id) {
            return categories.findById(id);
        }

        /** Loads several categories; an empty input gives an empty list without querying. */
        @Override
        public List<UnitCategory> findAllById(Collection<UUID> ids) {
            return ids.isEmpty() ? List.of() : categories.findByIdIn(ids);
        }

        /** The categories usable in the organization and, when given, on the site. */
        @Override
        public List<UnitCategory> usable(OrganizationId organization, Optional<SiteId> site, UnitType unitType,
                                         boolean includeArchived) {
            return search.usableCategories(organization, site, unitType, includeArchived);
        }

        /** Whether the same scope already has a category of this type and code. */
        @Override
        public boolean codeTaken(OrganizationId organization, Optional<SiteId> site, UnitType unitType, String code) {
            return search.categoryCodeTaken(organization, site, unitType, code);
        }

        /** The platform defaults. */
        @Override
        public List<UnitCategory> platformDefaults(UnitType unitType, boolean includeArchived) {
            return categories.platformDefaults(unitType, includeArchived);
        }

        /** Whether a platform default of this type has this code. */
        @Override
        public boolean platformCodeTaken(UnitType unitType, String code) {
            return categories.platformCodeTaken(unitType, code);
        }

        /** Inserts or updates a category. */
        @Override
        public UnitCategory save(UnitCategory category) {
            return categories.save(category);
        }
    }
}
