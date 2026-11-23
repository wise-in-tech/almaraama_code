package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.PreRegistration;
import com.almaraama.membership.domain.PreRegistrationRepository;
import com.almaraama.membership.domain.PreRegistrationStatus;
import com.almaraama.membership.domain.SupportSession;
import com.almaraama.membership.domain.SupportSessionRepository;
import java.time.Instant;
import com.almaraama.membership.domain.RegistrationQr;
import com.almaraama.membership.domain.RegistrationQrRepository;
import com.almaraama.membership.domain.MembershipStatus;
import com.almaraama.membership.domain.Occupancy;
import com.almaraama.membership.domain.OccupancyRepository;
import com.almaraama.membership.domain.OccupancyRole;
import com.almaraama.membership.domain.PermissionAdjustment;
import com.almaraama.membership.domain.PermissionAdjustmentRepository;
import com.almaraama.membership.domain.RoleAssignment;
import com.almaraama.membership.domain.RoleAssignmentRepository;
import com.almaraama.membership.domain.OrganizationRoleDelta;
import com.almaraama.membership.domain.Role;
import com.almaraama.membership.domain.RoleRepository;
import com.almaraama.membership.domain.SiteMembership;
import com.almaraama.membership.domain.SiteMembershipRepository;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapters from the domain's out ports to Spring Data JPA, in schema {@code membership} only (CF-3). */
final class JpaMembershipRepositories {

    /** Holder of the adapter classes only. */
    private JpaMembershipRepositories() {
    }

    /** {@link RoleRepository}: the read-only role tables, and the organizations' differences (C11). */
    @Component
    static class Roles implements RoleRepository {
        private final JdbcRoleCatalog catalog;
        private final SpringDataRoleDeltas deltas;

        /**
         * @param catalog reads the roles and their defaults
         * @param deltas  Spring Data repository of the differences
         */
        Roles(JdbcRoleCatalog catalog, SpringDataRoleDeltas deltas) {
            this.catalog = catalog;
            this.deltas = deltas;
        }

        /** Every active role, by sort order. */
        @Override
        public List<Role> roles() {
            return catalog.roles();
        }

        /** Every role's default permissions. */
        @Override
        public Map<RoleCode, Set<Permission>> defaults() {
            return catalog.defaults();
        }

        /** The active differences of these organizations; an empty input gives an empty list without querying. */
        @Override
        public List<OrganizationRoleDelta> activeDeltas(Collection<OrganizationId> organizations) {
            return organizations.isEmpty() ? List.of() : deltas.findByOrganizationIdInAndCancelledAtIsNull(
                    organizations.stream().map(OrganizationId::value).toList());
        }

        /** Inserts or updates a difference. */
        @Override
        public OrganizationRoleDelta save(OrganizationRoleDelta delta) {
            return deltas.save(delta);
        }
    }

    /** {@link RoleAssignmentRepository} on the {@code membership.role_assignment} table. */
    @Component
    static class RoleAssignments implements RoleAssignmentRepository {
        private final SpringDataRoleAssignments assignments;
        private final EntityManager entityManager;

        /**
         * @param assignments Spring Data repository of the role assignments
         * @param entityManager builds the search queries
         */
        RoleAssignments(SpringDataRoleAssignments assignments, EntityManager entityManager) {
            this.assignments = assignments;
            this.entityManager = entityManager;
        }

        /** Loads a role assignment. */
        @Override
        public Optional<RoleAssignment> findById(UUID id) {
            return assignments.findById(id);
        }

        /** The person's roles still in force. */
        @Override
        public List<RoleAssignment> activeOfPerson(PersonId personId) {
            return assignments.findByPersonIdAndEndedAtIsNull(personId.value());
        }

        /** Roles in force on the organization or its sites. */
        @Override
        public List<RoleAssignment> activeInOrganization(OrganizationId organizationId) {
            return assignments.findByOrganizationIdAndEndedAtIsNull(organizationId.value());
        }

        /** Criteria API: every value is a bound parameter (security baseline). */
        @Override
        public PageResult<RoleAssignment> search(OrganizationId organizationId, Filter filter, PageQuery page) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<RoleAssignment> query = cb.createQuery(RoleAssignment.class);
            Root<RoleAssignment> root = query.from(RoleAssignment.class);
            query.select(root).where(where(cb, root, organizationId, filter))
                    .orderBy(cb.desc(root.get("assignedAt")));
            List<RoleAssignment> items = entityManager.createQuery(query)
                    .setFirstResult((int) page.offset()).setMaxResults(page.size()).getResultList();
            CriteriaQuery<Long> count = cb.createQuery(Long.class);
            Root<RoleAssignment> counted = count.from(RoleAssignment.class);
            count.select(cb.count(counted)).where(where(cb, counted, organizationId, filter));
            return new PageResult<>(items, page.page(), page.size(), entityManager.createQuery(count).getSingleResult());
        }

        /** The conditions of the search, every value as a bound parameter. */
        private static Predicate[] where(CriteriaBuilder cb, Root<RoleAssignment> root, OrganizationId organization,
                                         Filter filter) {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("organizationId"), organization.value()));
            if (!filter.includeEnded()) {
                where.add(cb.isNull(root.get("endedAt")));
            }
            if (filter.personId() != null) {
                where.add(cb.equal(root.get("personId"), filter.personId().value()));
            }
            if (filter.siteId() != null) {
                where.add(cb.equal(root.get("siteId"), filter.siteId().value()));
            }
            if (filter.role() != null) {
                where.add(cb.equal(root.get("role"), filter.role().value()));
            }
            return where.toArray(Predicate[]::new);
        }

        /** The person's assignment of this role on this exact scope, if in force. */
        @Override
        public Optional<RoleAssignment> active(PersonId personId, RoleCode role, ScopeType scope,
                                               OrganizationId organizationId, SiteId siteId) {
            return activeOfPerson(personId).stream()
                    .filter(a -> a.role().equals(role) && a.scopeType() == scope)
                    .filter(a -> Objects.equals(a.organizationId().orElse(null), organizationId))
                    .filter(a -> Objects.equals(a.siteId().orElse(null), siteId))
                    .findFirst();
        }

        /** How many assignments of these roles are in force on the organization itself (RB-2). */
        @Override
        public long countActive(Set<RoleCode> roles, OrganizationId organizationId) {
            return roles.isEmpty() ? 0 : assignments.countActiveOnOrganization(
                    roles.stream().map(RoleCode::value).toList(), organizationId.value());
        }

        /** Inserts or updates a role assignment. */
        @Override
        public RoleAssignment save(RoleAssignment assignment) {
            return assignments.save(assignment);
        }
    }

    /** {@link PermissionAdjustmentRepository} on the {@code membership.permission_adjustment} table. */
    @Component
    static class Adjustments implements PermissionAdjustmentRepository {
        private final SpringDataPermissionAdjustments adjustments;

        /** @param adjustments Spring Data repository of the adjustments */
        Adjustments(SpringDataPermissionAdjustments adjustments) {
            this.adjustments = adjustments;
        }

        /** Loads an adjustment. */
        @Override
        public Optional<PermissionAdjustment> findById(UUID id) {
            return adjustments.findById(id);
        }

        /** The person's adjustments not cancelled. */
        @Override
        public List<PermissionAdjustment> activeOfPerson(PersonId personId) {
            return adjustments.findByPersonIdAndCancelledAtIsNull(personId.value());
        }

        /** Whether the same permission is already adjusted on the same scope. */
        @Override
        public boolean activeExists(PersonId personId, OrganizationId organizationId, Optional<SiteId> siteId,
                                    Permission permission) {
            return activeOfPerson(personId).stream().anyMatch(a -> a.organizationId().equals(organizationId)
                    && a.siteId().equals(siteId) && a.permission() == permission);
        }

        /** People of the organization with at least one adjustment in force. */
        @Override
        public List<PersonId> personsWithActiveAdjustments(OrganizationId organizationId) {
            return adjustments.personsWithActiveAdjustments(organizationId.value()).stream()
                    .map(PersonId::new).toList();
        }

        /** Inserts or updates an adjustment. */
        @Override
        public PermissionAdjustment save(PermissionAdjustment adjustment) {
            return adjustments.save(adjustment);
        }
    }

    /** {@link SiteMembershipRepository} on the {@code membership.site_membership} table. */
    @Component
    static class Memberships implements SiteMembershipRepository {
        private final SpringDataSiteMemberships memberships;

        /** @param memberships Spring Data repository of the site accesses */
        Memberships(SpringDataSiteMemberships memberships) {
            this.memberships = memberships;
        }

        /** Loads a site access. */
        @Override
        public Optional<SiteMembership> findById(UUID id) {
            return memberships.findById(id);
        }

        /** Every access of the person, newest first. */
        @Override
        public List<SiteMembership> ofPerson(PersonId personId) {
            return memberships.findByPersonIdOrderByCreatedAtDesc(personId.value());
        }

        /** The person's access to the site that is not rejected. */
        @Override
        public Optional<SiteMembership> current(PersonId personId, SiteId siteId) {
            return memberships.findByPersonIdAndSiteIdAndStatusNot(personId.value(), siteId.value(),
                    MembershipStatus.REJECTED);
        }

        /** Accesses of the site, newest first, optionally with one status. */
        @Override
        public List<SiteMembership> ofSite(SiteId siteId, MembershipStatus status) {
            return status == null ? memberships.findBySiteIdOrderByCreatedAtDesc(siteId.value())
                    : memberships.findBySiteIdAndStatusOrderByCreatedAtDesc(siteId.value(), status);
        }

        /** Active accesses in the organization's sites. */
        @Override
        public List<SiteMembership> activeInOrganization(OrganizationId organizationId) {
            return memberships.findByOrganizationIdAndStatus(organizationId.value(), MembershipStatus.ACTIVE);
        }

        /** Inserts or updates a site access. */
        @Override
        public SiteMembership save(SiteMembership membership) {
            return memberships.save(membership);
        }
    }

    /** {@link OccupancyRepository} on the {@code membership.occupancy} table. */
    @Component
    static class Occupancies implements OccupancyRepository {
        private final SpringDataOccupancies occupancies;

        /** @param occupancies Spring Data repository of the occupancies */
        Occupancies(SpringDataOccupancies occupancies) {
            this.occupancies = occupancies;
        }

        /** Loads an occupancy. */
        @Override
        public Optional<Occupancy> findById(UUID id) {
            return occupancies.findById(id);
        }

        /** Occupancies of a unit, current ones first. */
        @Override
        public List<Occupancy> ofUnit(UnitId unitId, boolean includeEnded) {
            if (!includeEnded) {
                return occupancies.findByUnitIdAndEndedOnIsNullOrderByStartedOnDesc(unitId.value());
            }
            List<Occupancy> all = new ArrayList<>(occupancies.findByUnitIdOrderByEndedOnDescStartedOnDesc(
                    unitId.value()));
            all.sort((a, b) -> Boolean.compare(b.active(), a.active()));
            return all;
        }

        /** The person's occupancies not ended. */
        @Override
        public List<Occupancy> activeOfPerson(PersonId personId) {
            return occupancies.findByPersonIdAndEndedOnIsNull(personId.value());
        }

        /** The person's occupancies not ended in one site. */
        @Override
        public List<Occupancy> activeOfPersonInSite(PersonId personId, SiteId siteId) {
            return occupancies.findByPersonIdAndSiteIdAndEndedOnIsNull(personId.value(), siteId.value());
        }

        /** The unit's current owner, if any (E5). */
        @Override
        public Optional<Occupancy> activeOwner(UnitId unitId) {
            return occupancies.findByUnitIdAndRoleAndEndedOnIsNull(unitId.value(), OccupancyRole.OWNER);
        }

        /** The unit's current primary tenant, if any (E3). */
        @Override
        public Optional<Occupancy> activePrimaryTenant(UnitId unitId) {
            return occupancies.findByUnitIdAndPrimaryTenantTrueAndEndedOnIsNull(unitId.value());
        }

        /** The person's current occupancy of the unit, if any. */
        @Override
        public Optional<Occupancy> active(PersonId personId, UnitId unitId) {
            return occupancies.findByPersonIdAndUnitIdAndEndedOnIsNull(personId.value(), unitId.value());
        }

        /** Inserts or updates an occupancy. */
        @Override
        public Occupancy save(Occupancy occupancy) {
            return occupancies.save(occupancy);
        }
    }

    /** {@link RegistrationQrRepository} on the {@code membership.registration_qr} table. */
    @Component
    static class RegistrationQrs implements RegistrationQrRepository {
        private final SpringDataRegistrationQrs codes;

        /** @param codes Spring Data repository of the registration QR codes */
        RegistrationQrs(SpringDataRegistrationQrs codes) {
            this.codes = codes;
        }

        /** The site's registration code, absent until first asked. */
        @Override
        public Optional<RegistrationQr> ofSite(SiteId siteId) {
            return codes.findById(siteId.value());
        }

        /** The registration code with this normalized value. */
        @Override
        public Optional<RegistrationQr> byCode(String normalizedCode) {
            return codes.findByCode(normalizedCode);
        }

        /** Inserts or updates a registration code. */
        @Override
        public RegistrationQr save(RegistrationQr registrationQr) {
            return codes.save(registrationQr);
        }
    }

    /** {@link PreRegistrationRepository} on the {@code membership.pre_registration} table. */
    @Component
    static class PreRegistrations implements PreRegistrationRepository {
        private final SpringDataPreRegistrations preRegistrations;

        /** @param preRegistrations Spring Data repository of the pre-registrations */
        PreRegistrations(SpringDataPreRegistrations preRegistrations) {
            this.preRegistrations = preRegistrations;
        }

        /** One pre-registration. */
        @Override
        public Optional<PreRegistration> findById(UUID id) {
            return preRegistrations.findById(id);
        }

        /** The pre-registration whose current key has this hash. */
        @Override
        public Optional<PreRegistration> byKeyHash(String keyHash) {
            return preRegistrations.findByKeyHash(keyHash);
        }

        /** Every pre-registration of a site, newest first. */
        @Override
        public List<PreRegistration> ofSite(SiteId siteId) {
            return preRegistrations.findBySiteIdOrderByCreatedAtDesc(siteId.value());
        }

        /** Stored-pending pre-registrations of a site. */
        @Override
        public List<PreRegistration> pendingOfSite(SiteId siteId) {
            return preRegistrations.findBySiteIdAndStatus(siteId.value(), PreRegistrationStatus.PENDING);
        }

        /** Inserts or updates a pre-registration. */
        @Override
        public PreRegistration save(PreRegistration preRegistration) {
            return preRegistrations.save(preRegistration);
        }
    }

    /** {@link SupportSessionRepository} on the {@code membership.support_session} table. */
    @Component
    static class SupportSessions implements SupportSessionRepository {
        private final SpringDataSupportSessions sessions;

        /** @param sessions Spring Data repository of the support sessions */
        SupportSessions(SpringDataSupportSessions sessions) {
            this.sessions = sessions;
        }

        /** One session. */
        @Override
        public Optional<SupportSession> findById(UUID id) {
            return sessions.findById(id);
        }

        /** The administrator's newest open session. */
        @Override
        public Optional<SupportSession> openOf(PersonId administrator, Instant now) {
            return sessions.findByPersonIdAndEndedAtIsNullAndExpiresAtAfterOrderByStartedAtDesc(administrator.value(),
                    now).stream().findFirst();
        }

        /** Open sessions on an organization. */
        @Override
        public List<SupportSession> openOn(OrganizationId organization, Instant now) {
            return sessions.findByOrganizationIdAndEndedAtIsNullAndExpiresAtAfter(organization.value(), now);
        }

        /** Sessions, newest first, optionally of one organization. */
        @Override
        public List<SupportSession> list(Optional<OrganizationId> organization) {
            return organization.map(o -> sessions.findByOrganizationIdOrderByStartedAtDesc(o.value()))
                    .orElseGet(sessions::findAllByOrderByStartedAtDesc);
        }

        /** Inserts or updates a session. */
        @Override
        public SupportSession save(SupportSession session) {
            return sessions.save(session);
        }
    }
}
