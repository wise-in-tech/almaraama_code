package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.OrganizationRepository;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.sites.api.UnitType;
import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.AddressOwnerType;
import com.almaraama.sites.domain.LocalizedName;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.SiteRepository;
import com.almaraama.sites.domain.UnitCategory;
import com.almaraama.sites.domain.Zone;
import com.almaraama.sites.domain.ZoneRepository;
import com.almaraama.sites.domain.ZoneStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/**
 * Searches with optional criteria over sites, zones and unit categories, built with the JPA Criteria API: every value
 * is a bound parameter, never concatenated into the query (security baseline). The zone filter of the sites uses the
 * materialized path of the zone tree, so a wilaya selects every site of its communes (A8, criterion 3).
 */
@Component
class JpaStructureSearch {

    private final EntityManager entityManager;

    /** @param entityManager builds the criteria queries */
    JpaStructureSearch(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** One page of the organization's sites matching the filter, by name, and their total count. */
    PageResult<Site> sites(OrganizationId organization, Collection<UUID> onlyIds, SiteRepository.Filter filter,
                           PageQuery page) {
        return page(Site.class, (cb, query) -> {
            Root<Site> site = query.from(Site.class);
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(site.get("organizationId"), organization.value()));
            if (onlyIds != null) {
                where.add(onlyIds.isEmpty() ? cb.disjunction() : site.get("id").in(onlyIds));
            }
            if (!filter.includeInactive()) {
                where.add(cb.isTrue(site.get("active")));
            }
            if (filter.namePart() != null && !filter.namePart().isBlank()) {
                where.add(cb.like(cb.lower(site.get("name")), contains(filter.namePart()), '\\'));
            }
            if (filter.zonePath() != null) {
                Subquery<UUID> inZone = query.subquery(UUID.class);
                Root<Address> address = inZone.from(Address.class);
                Root<Zone> zone = inZone.from(Zone.class);
                inZone.select(address.get("ownerId")).where(
                        cb.equal(address.get("ownerType"), AddressOwnerType.SITE),
                        cb.isNull(address.get("replacedAt")),
                        cb.equal(address.get("zoneId"), zone.get("id")),
                        cb.like(zone.get("path"), escape(filter.zonePath()) + "%", '\\'));
                where.add(site.get("id").in(inZone));
            }
            return new Query<>(site, where, cb.asc(cb.lower(site.get("name"))));
        }, page);
    }

    /**
     * One page of zones of a country: by name in any language across levels, or the children of a parent (level 1
     * without parent), optionally of one level; deactivated zones left out; by code.
     */
    PageResult<Zone> zones(ZoneRepository.Filter filter, PageQuery page) {
        return page(Zone.class, (cb, query) -> {
            Root<Zone> zone = query.from(Zone.class);
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(zone.get("countryCode"), filter.countryCode()));
            where.add(cb.notEqual(zone.get("status"), ZoneStatus.DEACTIVATED));
            if (filter.namePart() != null) {
                String pattern = contains(filter.namePart());
                where.add(cb.or(cb.like(cb.lower(zone.get("nameFr")), pattern, '\\'),
                        cb.like(cb.lower(zone.get("nameEn")), pattern, '\\'),
                        cb.like(zone.get("nameAr"), "%" + escape(filter.namePart().strip()) + "%", '\\')));
            } else if (filter.parentId() != null) {
                where.add(cb.equal(zone.get("parentId"), filter.parentId()));
            } else if (filter.level() == null) {
                where.add(cb.equal(zone.get("level"), 1));
            }
            if (filter.level() != null) {
                where.add(cb.equal(zone.get("level"), filter.level()));
            }
            return new Query<>(zone, where, cb.asc(zone.get("code")));
        }, page);
    }

    /**
     * Back-office (ADR-035): one page of zones of a country in every status, optionally of one status, under one
     * parent, or matching a name in any language; by code.
     */
    PageResult<Zone> adminZones(ZoneRepository.AdminFilter filter, PageQuery page) {
        return page(Zone.class, (cb, query) -> {
            Root<Zone> zone = query.from(Zone.class);
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(zone.get("countryCode"), filter.countryCode()));
            if (filter.status() != null) {
                where.add(cb.equal(zone.get("status"), filter.status()));
            }
            if (filter.parentId() != null) {
                where.add(cb.equal(zone.get("parentId"), filter.parentId()));
            }
            if (filter.namePart() != null) {
                String pattern = contains(filter.namePart());
                where.add(cb.or(cb.like(cb.lower(zone.get("nameFr")), pattern, '\\'),
                        cb.like(cb.lower(zone.get("nameEn")), pattern, '\\'),
                        cb.like(cb.lower(zone.get("code")), pattern, '\\'),
                        cb.like(zone.get("nameAr"), "%" + escape(filter.namePart().strip()) + "%", '\\')));
            }
            return new Query<>(zone, where, cb.asc(zone.get("code")));
        }, page);
    }

    /** Back-office (ADR-035): one page of every organization by name, filtered by name, state and country. */
    PageResult<Organization> organizations(OrganizationRepository.Search search, PageQuery page) {
        return page(Organization.class, (cb, query) -> {
            Root<Organization> organization = query.from(Organization.class);
            List<Predicate> where = new ArrayList<>();
            if (search.namePart() != null && !search.namePart().isBlank()) {
                where.add(cb.like(cb.lower(organization.get("name")), contains(search.namePart()), '\\'));
            }
            if (search.active() != null) {
                where.add(cb.equal(organization.get("active"), search.active()));
            }
            if (search.countryCode() != null) {
                where.add(cb.equal(organization.get("countryCode"), search.countryCode()));
            }
            return new Query<>(organization, where, cb.asc(cb.lower(organization.get("name"))));
        }, page);
    }

    /** Whether a zone of the country has this name, in any of its languages, under the same parent. */
    boolean zoneNameTaken(String countryCode, UUID parentId, LocalizedName name) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Zone> zone = query.from(Zone.class);
        List<Predicate> names = new ArrayList<>();
        addSameName(cb, names, zone.get("nameAr"), name.ar());
        addSameName(cb, names, zone.get("nameEn"), name.en());
        addSameName(cb, names, zone.get("nameFr"), name.fr());
        query.select(cb.count(zone)).where(
                cb.equal(zone.get("countryCode"), countryCode),
                parentId == null ? cb.isNull(zone.get("parentId")) : cb.equal(zone.get("parentId"), parentId),
                cb.or(names.toArray(Predicate[]::new)));
        return entityManager.createQuery(query).getSingleResult() > 0;
    }

    /**
     * The unit categories usable in an organization (and on one site): platform defaults, the organization's and the
     * site's own; by unit type then sort order and code.
     */
    List<UnitCategory> usableCategories(OrganizationId organization, Optional<SiteId> site, UnitType unitType,
                                        boolean includeArchived) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UnitCategory> query = cb.createQuery(UnitCategory.class);
        Root<UnitCategory> category = query.from(UnitCategory.class);
        List<Predicate> scopes = new ArrayList<>();
        scopes.add(cb.equal(category.get("scopeType"), ScopeType.PLATFORM));
        scopes.add(cb.and(cb.equal(category.get("scopeType"), ScopeType.ORGANIZATION),
                cb.equal(category.get("organizationId"), organization.value())));
        site.ifPresent(id -> scopes.add(cb.and(cb.equal(category.get("scopeType"), ScopeType.SITE),
                cb.equal(category.get("siteId"), id.value()))));
        List<Predicate> where = new ArrayList<>();
        where.add(cb.or(scopes.toArray(Predicate[]::new)));
        if (unitType != null) {
            where.add(cb.equal(category.get("unitType"), unitType));
        }
        if (!includeArchived) {
            where.add(cb.isTrue(category.get("active")));
        }
        query.select(category).where(where.toArray(Predicate[]::new))
                .orderBy(cb.asc(category.get("unitType")), cb.asc(category.get("sortOrder")),
                        cb.asc(category.get("code")));
        return entityManager.createQuery(query).getResultList();
    }

    /** Whether this exact scope already has a category of this type and code. */
    boolean categoryCodeTaken(OrganizationId organization, Optional<SiteId> site, UnitType unitType, String code) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<UnitCategory> category = query.from(UnitCategory.class);
        query.select(cb.count(category)).where(
                cb.equal(category.get("organizationId"), organization.value()),
                site.map(id -> cb.equal(category.get("siteId"), id.value()))
                        .orElseGet(() -> cb.isNull(category.get("siteId"))),
                cb.equal(category.get("unitType"), unitType),
                cb.equal(category.get("code"), code));
        return entityManager.createQuery(query).getSingleResult() > 0;
    }

    /** The root, conditions and order of one search, shared by its page query and its count query. */
    private record Query<T>(Root<T> root, List<Predicate> where, jakarta.persistence.criteria.Order order) {
    }

    /** Runs a search twice: one page of results, then the total count with the same conditions. */
    private <T> PageResult<T> page(Class<T> type, BiFunction<CriteriaBuilder, CriteriaQuery<?>, Query<T>> build,
                                   PageQuery page) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> select = cb.createQuery(type);
        Query<T> rows = build.apply(cb, select);
        select.select(rows.root()).where(rows.where().toArray(Predicate[]::new)).orderBy(rows.order());
        List<T> items = entityManager.createQuery(select)
                .setFirstResult((int) page.offset())
                .setMaxResults(page.size())
                .getResultList();

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Query<T> counted = build.apply(cb, count);
        count.select(cb.count(counted.root())).where(counted.where().toArray(Predicate[]::new));
        long total = entityManager.createQuery(count).getSingleResult();
        return new PageResult<>(items, page.page(), page.size(), total);
    }

    /** Adds "same name, ignoring case" for one language when a name is given in it. */
    private static void addSameName(CriteriaBuilder cb, List<Predicate> names, Path<String> column, String value) {
        if (value != null && !value.isBlank()) {
            Expression<String> lower = cb.lower(column);
            names.add(cb.equal(lower, value.strip().toLowerCase(Locale.ROOT)));
        }
    }

    /** {@code %part%} in lower case, with the {@code LIKE} wildcards escaped. */
    private static String contains(String part) {
        return "%" + escape(part.strip().toLowerCase(Locale.ROOT)) + "%";
    }

    /** Escapes the {@code LIKE} wildcards. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
