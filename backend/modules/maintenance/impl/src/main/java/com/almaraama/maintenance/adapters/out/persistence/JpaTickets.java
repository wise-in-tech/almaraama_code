package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.MaintenanceRepositories.Readership;
import com.almaraama.maintenance.domain.MaintenanceRepositories.TicketFilter;
import com.almaraama.maintenance.domain.MaintenanceRepositories.Tickets;
import com.almaraama.maintenance.domain.Ticket;
import com.almaraama.maintenance.domain.TicketActivity;
import com.almaraama.maintenance.domain.TicketAssignment;
import com.almaraama.maintenance.domain.TicketPhoto;
import com.almaraama.maintenance.domain.Visibility;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link Tickets} on the ticket tables. The list query applies the visibility of ADR-037 with the Criteria API: every
 * value is a bound parameter, never concatenated (security baseline).
 */
@Component
class JpaTickets implements Tickets {

    private final SpringDataTickets tickets;
    private final SpringDataTicketPhotos photos;
    private final SpringDataTicketAssignments assignments;
    private final SpringDataTicketActivities activities;
    private final EntityManager entityManager;

    /**
     * @param tickets       tickets
     * @param photos        photos
     * @param assignments   assignments
     * @param activities    activities
     * @param entityManager criteria queries and the reference counter
     */
    JpaTickets(SpringDataTickets tickets, SpringDataTicketPhotos photos, SpringDataTicketAssignments assignments,
               SpringDataTicketActivities activities, EntityManager entityManager) {
        this.tickets = tickets;
        this.photos = photos;
        this.assignments = assignments;
        this.activities = activities;
        this.entityManager = entityManager;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Ticket> findById(UUID id) {
        return tickets.findById(id);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<Ticket> search(Readership readership, TicketFilter filter, PageQuery page) {
        if (readership.siteRules().isEmpty() && readership.organizationRules().isEmpty()) {
            return new PageResult<>(List.of(), page.page(), page.size(), 0);
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Ticket> query = cb.createQuery(Ticket.class);
        Root<Ticket> ticket = query.from(Ticket.class);
        query.select(ticket).where(predicates(cb, query, ticket, readership, filter))
                .orderBy(cb.desc(ticket.get("lastChangeAt")));
        List<Ticket> items = entityManager.createQuery(query).setFirstResult((int) page.offset())
                .setMaxResults(page.size()).getResultList();
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<Ticket> counted = count.from(Ticket.class);
        count.select(cb.count(counted)).where(predicates(cb, count, counted, readership, filter));
        return new PageResult<>(items, page.page(), page.size(), entityManager.createQuery(count).getSingleResult());
    }

    /** Visibility (ADR-037) and the filters of the list. */
    private static Predicate[] predicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Ticket> t,
                                          Readership readership, TicketFilter filter) {
        Subquery<UUID> assigned = query.subquery(UUID.class);
        Root<TicketAssignment> a = assigned.from(TicketAssignment.class);
        assigned.select(a.get("ticketId")).where(cb.equal(a.get("technicianId"), readership.person()),
                cb.isNull(a.get("removedAt")));
        Predicate related = cb.or(cb.equal(t.get("requesterId"), readership.person()),
                t.get("id").in(assigned));
        Predicate common = cb.equal(t.get("visibility"), Visibility.COMMON);
        Predicate privateOne = cb.equal(t.get("visibility"), Visibility.PRIVATE);

        List<Predicate> scopes = new ArrayList<>();
        for (Readership.SiteRule rule : readership.siteRules()) {
            List<Predicate> any = new ArrayList<>();
            any.add(related);
            if (rule.commonAll()) {
                any.add(common);
            } else if (rule.member()) {
                any.add(cb.and(common, rule.buildings().isEmpty() ? cb.conjunction()
                        : cb.or(cb.isNull(t.get("buildingId")), t.get("buildingId").in(rule.buildings()))));
            }
            if (rule.privateAll()) {
                any.add(privateOne);
            } else if (!rule.units().isEmpty()) {
                any.add(cb.and(privateOne, t.get("unitId").in(rule.units())));
            }
            scopes.add(cb.and(cb.equal(t.get("siteId"), rule.siteId().value()), cb.or(any.toArray(Predicate[]::new))));
        }
        for (Readership.OrganizationRule rule : readership.organizationRules()) {
            List<Predicate> any = new ArrayList<>();
            any.add(related);
            if (rule.commonAll()) {
                any.add(common);
            }
            if (rule.privateAll()) {
                any.add(privateOne);
            }
            scopes.add(cb.and(cb.equal(t.get("organizationId"), rule.organizationId().value()),
                    cb.or(any.toArray(Predicate[]::new))));
        }
        List<Predicate> where = new ArrayList<>();
        where.add(cb.or(scopes.toArray(Predicate[]::new)));
        filter.siteId().ifPresent(s -> where.add(cb.equal(t.get("siteId"), s.value())));
        if ("MINE".equals(filter.view())) {
            where.add(cb.equal(t.get("requesterId"), readership.person()));
        } else if ("COMMON".equals(filter.view())) {
            where.add(common);
        }
        if (filter.categories() != null && !filter.categories().isEmpty()) {
            where.add(t.get("category").in(filter.categories()));
        }
        filter.issueTypeId().ifPresent(id -> where.add(cb.equal(t.get("issueTypeId"), id)));
        filter.buildingId().ifPresent(id -> where.add(cb.equal(t.get("buildingId"), id)));
        filter.assetId().ifPresent(id -> where.add(cb.equal(t.get("assetId"), id)));
        filter.reference().map(r -> r.strip().toUpperCase(Locale.ROOT)).filter(r -> !r.isEmpty())
                .ifPresent(r -> where.add(cb.equal(t.get("reference"), r)));
        return where.toArray(Predicate[]::new);
    }

    /** {@inheritDoc} Atomic: an upsert on the counter row of the organization. */
    @Override
    public String nextReference(OrganizationId organization) {
        Number next = (Number) entityManager.createNativeQuery("""
                INSERT INTO maintenance.ticket_counter (organization_id, last_value) VALUES (?1, 1)
                ON CONFLICT (organization_id) DO UPDATE SET last_value = maintenance.ticket_counter.last_value + 1
                RETURNING last_value""").setParameter(1, organization.value()).getSingleResult();
        return "T-" + String.format("%06d", next.longValue());
    }

    /** {@inheritDoc} */
    @Override
    public Ticket save(Ticket ticket) {
        return tickets.save(ticket);
    }

    /** {@inheritDoc} */
    @Override
    public List<TicketPhoto> photos(UUID ticketId) {
        return photos.findByTicketIdOrderByAddedAtAsc(ticketId);
    }

    /** {@inheritDoc} */
    @Override
    public Map<UUID, Long> photoCounts(Collection<UUID> ticketIds) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!ticketIds.isEmpty()) {
            for (Object[] row : photos.counts(ticketIds)) {
                counts.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    /** {@inheritDoc} */
    @Override
    public TicketPhoto save(TicketPhoto photo) {
        return photos.save(photo);
    }

    /** {@inheritDoc} */
    @Override
    public List<TicketAssignment> assignments(UUID ticketId) {
        return assignments.active(ticketId);
    }

    /** {@inheritDoc} */
    @Override
    public boolean assigned(UUID ticketId, PersonId person) {
        return assignments.assigned(ticketId, person.value());
    }

    /** {@inheritDoc} */
    @Override
    public TicketAssignment save(TicketAssignment assignment) {
        return assignments.save(assignment);
    }

    /** {@inheritDoc} */
    @Override
    public List<TicketActivity> activities(UUID ticketId) {
        return activities.findByTicketIdOrderByOccurredAtAsc(ticketId);
    }

    /** {@inheritDoc} */
    @Override
    public TicketActivity save(TicketActivity activity) {
        return activities.save(activity);
    }
}
