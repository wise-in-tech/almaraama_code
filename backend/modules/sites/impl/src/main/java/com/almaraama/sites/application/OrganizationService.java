package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.OrganizationRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading and updating client organizations (A1). Creation goes through {@link SitesApiService} (onboarding by our
 * team, C12).
 */
@Service
public class OrganizationService {

    /** What a person enters to change an organization, with the version they read. */
    public record UpdateOrganization(String name, String contactPhone, String contactEmail, long version) {
    }

    private final OrganizationRepository organizations;
    private final Clock clock;

    /**
     * @param organizations the organization table
     * @param clock time of changes
     */
    OrganizationService(OrganizationRepository organizations, Clock clock) {
        this.organizations = organizations;
        this.clock = clock;
    }

    /** Our team sees every organization; anyone else only those of their perimeter (M3, D5). */
    @Transactional(readOnly = true)
    public PageResult<Organization> list(Perimeter perimeter, PageQuery page) {
        if (perimeter.canOnPlatform(Permission.ORGANIZATION_CREATE)) {
            return organizations.page(page);
        }
        Set<OrganizationId> visible = new HashSet<>(perimeter.organizations().keySet());
        perimeter.sites().values().forEach(site -> visible.add(site.organizationId()));
        return organizations.page(visible, page);
    }

    /** One organization; {@code NOT_FOUND} outside the perimeter (our team sees every organization). */
    @Transactional(readOnly = true)
    public Organization get(Perimeter perimeter, OrganizationId id) {
        if (!perimeter.canOnPlatform(Permission.ORGANIZATION_CREATE)) {
            perimeter.requireVisible(id);
        }
        return organizations.findById(id).orElseThrow(OrganizationService::notFound);
    }

    /**
     * Changes the name and contact; needs {@code organization:update} on the organization (its promoteur) or on the
     * platform (our team); {@code VERSION_CONFLICT}. Country and type are not changed here (M4, M5).
     */
    @Transactional
    public Organization update(Perimeter perimeter, OrganizationId id, UpdateOrganization command) {
        if (!perimeter.canOnPlatform(Permission.ORGANIZATION_UPDATE)) {
            perimeter.require(Permission.ORGANIZATION_UPDATE, id);
        }
        Organization organization = organizations.findById(id).orElseThrow(OrganizationService::notFound);
        Versions.check(command.version(), organization.version());
        organization.update(command.name(), command.contactPhone(), command.contactEmail(), clock.instant());
        return organizations.save(organization);
    }

    /** The single answer for something missing or outside the perimeter (M3). */
    static BusinessException notFound() {
        return new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "Not found");
    }
}
