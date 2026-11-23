package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.UnitType;
import com.almaraama.sites.domain.LocalizedName;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.UnitCategory;
import com.almaraama.sites.domain.UnitCategoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit categories (A6, criterion 2): platform defaults, completed by a promoteur for its organization or by a manager
 * for one site. The defaults are kept by the platform administrators in the back-office (sprint 2), never here.
 */
@Service
public class UnitCategoryService {

    private static final Logger LOG = LoggerFactory.getLogger(UnitCategoryService.class);

    /** Where a category is added: the organization, or one site of it. */
    public record CategoryScope(OrganizationId organizationId, Optional<SiteId> siteId) {
    }

    /** What a person enters to add a category. */
    public record CreateCategory(ScopeType scopeType, UUID scopeId, UnitType unitType, String code, LocalizedName name,
                                 int sortOrder) {
    }

    private final UnitCategoryRepository categories;
    private final SiteService sites;
    private final Clock clock;

    /**
     * @param categories the category table
     * @param sites      visible sites and their organization
     * @param clock      time of changes
     */
    UnitCategoryService(UnitCategoryRepository categories, SiteService sites, Clock clock) {
        this.categories = categories;
        this.sites = sites;
        this.clock = clock;
    }

    /**
     * The categories usable on a site ({@code siteId}) or in an organization ({@code organizationId}); needs {@code
     * unit-category:read} there. Exactly one of the two: {@code VALIDATION_FAILED} otherwise.
     */
    @Transactional(readOnly = true)
    public List<UnitCategory> list(Perimeter perimeter, UUID siteId, UUID organizationId, UnitType unitType,
                                   boolean includeArchived) {
        if ((siteId == null) == (organizationId == null)) {
            throw new BusinessException("VALIDATION_FAILED", ErrorCategory.VALIDATION, "siteId or organizationId");
        }
        CategoryScope scope = siteId != null
                ? siteScope(perimeter, new SiteId(siteId), Permission.UNIT_CATEGORY_READ)
                : organizationScope(perimeter, new OrganizationId(organizationId), Permission.UNIT_CATEGORY_READ);
        return categories.usable(scope.organizationId(), scope.siteId(), unitType, includeArchived);
    }

    /**
     * Adds a category for the organization or one site; needs {@code unit-category:create} there; {@code
     * UNIT_CATEGORY_SCOPE_INVALID} for the platform scope, {@code UNIT_CATEGORY_CODE_TAKEN}, {@code
     * UNIT_CATEGORY_CODE_INVALID}, {@code UNIT_CATEGORY_NAME_INVALID}.
     */
    @Transactional
    public UnitCategory create(Perimeter perimeter, CreateCategory command) {
        CategoryScope scope = switch (command.scopeType()) {
            case ORGANIZATION -> organizationScope(perimeter,
                    new OrganizationId(requireId(command.scopeId())), Permission.UNIT_CATEGORY_CREATE);
            case SITE -> siteScope(perimeter, new SiteId(requireId(command.scopeId())),
                    Permission.UNIT_CATEGORY_CREATE);
            case PLATFORM -> throw new BusinessException("UNIT_CATEGORY_SCOPE_INVALID", ErrorCategory.RULE_VIOLATION,
                    "Platform defaults are kept in the back-office");
        };
        if (categories.codeTaken(scope.organizationId(), scope.siteId(), command.unitType(), command.code())) {
            throw new BusinessException("UNIT_CATEGORY_CODE_TAKEN", ErrorCategory.CONFLICT, "Code already used");
        }
        UnitCategory category = categories.save(UnitCategory.create(scope.organizationId(), scope.siteId(),
                command.unitType(), command.code(), command.name(), command.sortOrder(), clock.instant()));
        LOG.debug("Unit category {} {} created on {} {}", command.unitType(), command.code(), command.scopeType(),
                command.scopeId());
        return category;
    }

    /**
     * Renames, reorders, archives or reactivates a category; needs {@code unit-category:update}, and {@code
     * unit-category:archive} to change {@code active}; {@code UNIT_CATEGORY_NOT_EDITABLE} for a platform default,
     * {@code VERSION_CONFLICT}.
     */
    @Transactional
    public UnitCategory update(Perimeter perimeter, UUID categoryId, LocalizedName name, int sortOrder,
                               boolean active, long version) {
        UnitCategory category = categories.findById(categoryId).orElseThrow(OrganizationService::notFound);
        if (category.scopeType() == ScopeType.PLATFORM) {
            throw new BusinessException("UNIT_CATEGORY_NOT_EDITABLE", ErrorCategory.RULE_VIOLATION,
                    "Platform defaults are kept in the back-office");
        }
        OrganizationId organization = category.organizationId().orElseThrow();
        Optional<SiteId> site = category.siteId();
        if (site.isPresent()) {
            perimeter.require(Permission.UNIT_CATEGORY_UPDATE, organization, site.get());
        } else {
            perimeter.require(Permission.UNIT_CATEGORY_UPDATE, organization);
        }
        if (category.active() != active) {
            if (site.isPresent()) {
                perimeter.require(Permission.UNIT_CATEGORY_ARCHIVE, organization, site.get());
            } else {
                perimeter.require(Permission.UNIT_CATEGORY_ARCHIVE, organization);
            }
        }
        Versions.check(version, category.version());
        Instant now = clock.instant();
        category.update(name, sortOrder, active, now);
        return categories.save(category);
    }

    // ---------------------------------------------------------------------- platform defaults (A6, M7, ADR-035)

    /** The platform defaults; needs {@code reference-data:read} on the platform (criterion 2). */
    @Transactional(readOnly = true)
    public List<UnitCategory> platformDefaults(Perimeter perimeter, UnitType unitType, boolean includeArchived) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_READ);
        return categories.platformDefaults(unitType, includeArchived);
    }

    /**
     * A6, M7: adds a default category offered to every organization; needs {@code reference-data:create}. {@code
     * UNIT_CATEGORY_CODE_INVALID}, {@code UNIT_CATEGORY_NAME_INVALID}, {@code UNIT_CATEGORY_CODE_TAKEN}.
     */
    @Transactional
    public UnitCategory createPlatformDefault(Perimeter perimeter, UnitType unitType, String code, LocalizedName name,
                                              int sortOrder) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_CREATE);
        if (categories.platformCodeTaken(unitType, code)) {
            throw new BusinessException("UNIT_CATEGORY_CODE_TAKEN", ErrorCategory.CONFLICT, "Code used");
        }
        return categories.save(UnitCategory.platformDefault(unitType, code, name, sortOrder, clock.instant()));
    }

    /**
     * Renames, reorders or archives a platform default; needs {@code reference-data:update}, and {@code
     * reference-data:archive} to change {@code active}. An archived category stays on its units (M2). {@code
     * UNIT_CATEGORY_NOT_PLATFORM}, {@code VERSION_CONFLICT}.
     */
    @Transactional
    public UnitCategory updatePlatformDefault(Perimeter perimeter, UUID categoryId, LocalizedName name, int sortOrder,
                                              boolean active, long version) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_UPDATE);
        UnitCategory category = categories.findById(categoryId).orElseThrow(OrganizationService::notFound);
        if (category.scopeType() != ScopeType.PLATFORM) {
            throw new BusinessException("UNIT_CATEGORY_NOT_PLATFORM", ErrorCategory.RULE_VIOLATION,
                    "An organization's or a site's category");
        }
        if (category.active() != active) {
            perimeter.requireOnPlatform(Permission.REFERENCE_DATA_ARCHIVE);
        }
        Versions.check(version, category.version());
        category.update(name, sortOrder, active, clock.instant());
        return categories.save(category);
    }

    /** A visible site where the caller holds the permission. */
    private CategoryScope siteScope(Perimeter perimeter, SiteId siteId, Permission permission) {
        Site site = sites.visible(perimeter, siteId);
        perimeter.require(permission, site.organizationId(), siteId);
        return new CategoryScope(site.organizationId(), Optional.of(siteId));
    }

    /** An organization where the caller holds the permission. */
    private static CategoryScope organizationScope(Perimeter perimeter, OrganizationId organization,
                                                   Permission permission) {
        perimeter.require(permission, organization);
        return new CategoryScope(organization, Optional.empty());
    }

    /** The scope identifier, mandatory for an organization or a site. */
    private static UUID requireId(UUID id) {
        if (id == null) {
            throw new BusinessException("VALIDATION_FAILED", ErrorCategory.VALIDATION, "scope.id is required");
        }
        return id;
    }
}
