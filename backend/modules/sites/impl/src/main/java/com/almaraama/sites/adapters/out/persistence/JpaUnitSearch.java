package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.sites.domain.Unit;
import com.almaraama.sites.domain.UnitRepository.Filter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Unit search with optional criteria, built with the JPA Criteria API: every value is a bound parameter, never
 * concatenated into the query (security baseline).
 */
@Component
class JpaUnitSearch {

    private final EntityManager entityManager;

    /** @param entityManager builds the criteria queries */
    JpaUnitSearch(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** One page of matching units ordered by reference, and their total count. */
    PageResult<Unit> search(SiteId siteId, Filter filter, PageQuery page) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Unit> query = cb.createQuery(Unit.class);
        Root<Unit> unit = query.from(Unit.class);
        query.select(unit).where(predicates(cb, unit, siteId, filter))
                .orderBy(cb.asc(cb.lower(unit.get("reference"))));
        List<Unit> items = entityManager.createQuery(query)
                .setFirstResult((int) page.offset())
                .setMaxResults(page.size())
                .getResultList();

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<Unit> counted = count.from(Unit.class);
        count.select(cb.count(counted)).where(predicates(cb, counted, siteId, filter));
        long total = entityManager.createQuery(count).getSingleResult();

        return new PageResult<>(items, page.page(), page.size(), total);
    }

    /** The conditions of the filter, every value as a bound parameter. */
    private static Predicate[] predicates(CriteriaBuilder cb, Root<Unit> unit, SiteId siteId, Filter filter) {
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(unit.get("siteId"), siteId.value()));
        if (!filter.includeInactive()) {
            where.add(cb.isTrue(unit.get("active")));
        }
        if (filter.buildingId() != null) {
            where.add(cb.equal(unit.get("buildingId"), filter.buildingId().value()));
        }
        if (filter.outdoorAreaId() != null) {
            where.add(cb.equal(unit.get("outdoorAreaId"), filter.outdoorAreaId()));
        }
        if (filter.type() != null) {
            where.add(cb.equal(unit.get("type"), filter.type()));
        }
        if (filter.categoryId() != null) {
            where.add(cb.equal(unit.get("categoryId"), filter.categoryId()));
        }
        if (filter.referencePart() != null && !filter.referencePart().isBlank()) {
            String escaped = filter.referencePart().strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            where.add(cb.like(cb.lower(unit.get("reference")), "%" + escaped + "%", '\\'));
        }
        filter.visibleBuildingIds().ifPresent(buildings -> where.add(buildings.isEmpty()
                ? cb.disjunction()
                : unit.get("buildingId").in(ids(buildings))));
        return where.toArray(Predicate[]::new);
    }

    /** Raw UUIDs of the visible buildings. */
    private static List<Object> ids(Set<BuildingId> buildings) {
        return buildings.stream().<Object>map(BuildingId::value).toList();
    }
}
