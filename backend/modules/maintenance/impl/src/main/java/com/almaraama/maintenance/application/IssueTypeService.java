package com.almaraama.maintenance.application;

import com.almaraama.assets.api.AssetView;
import com.almaraama.assets.api.AssetsApi;
import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.PersonSummary;
import com.almaraama.maintenance.api.events.IssueTypeChangedV1;
import com.almaraama.maintenance.api.events.SiteIssueTypeConfigChangedV1;
import com.almaraama.maintenance.domain.IssueCategory;
import com.almaraama.maintenance.domain.IssueType;
import com.almaraama.maintenance.domain.IssueTypeConfig;
import com.almaraama.maintenance.domain.MaintenanceRepositories.IssueTypes;
import com.almaraama.maintenance.domain.RoutingDestination;
import com.almaraama.maintenance.domain.Visibility;
import com.almaraama.membership.api.PerimeterApi;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issue categories and types (H2, H3, M7) and their routing and visibility per site (H4, H8, ADR-037). Platform
 * defaults are kept by the administrators ({@code reference-data:*}); an organization completes them with {@code
 * issue-type:manage} on itself or one of its sites.
 */
@Service
public class IssueTypeService {

    /** A category with the types the reader may use. */
    public record CategoryView(IssueCategory category, List<IssueType> types) {
    }

    /** The effective setting of a type on a site. */
    public record ConfigView(IssueType type, IssueCategory category, boolean configured, RoutingDestination routing,
                             List<PersonSummary> technicians, Visibility visibility, boolean active,
                             Optional<Long> version) {
    }

    /** What a person types for a type. */
    public record TypeCommand(String code, LocalizedText name, boolean assetRequired, Visibility defaultVisibility,
                              List<UUID> assetFamilyIds, int sortOrder) {
    }

    private final IssueTypes issueTypes;
    private final SitesApi sites;
    private final AssetsApi assets;
    private final PerimeterApi perimeters;
    private final IdentityApi identity;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param issueTypes categories, types, settings
     * @param sites      sites
     * @param assets     equipment (family filter)
     * @param perimeters permissions of the designated technicians
     * @param identity   names of people
     * @param events     audit events
     * @param clock      time of changes
     */
    IssueTypeService(IssueTypes issueTypes, SitesApi sites, AssetsApi assets, PerimeterApi perimeters,
                     IdentityApi identity, ApplicationEventPublisher events, Clock clock) {
        this.issueTypes = issueTypes;
        this.sites = sites;
        this.assets = assets;
        this.perimeters = perimeters;
        this.identity = identity;
        this.events = events;
        this.clock = clock;
    }

    /**
     * H2, criterion 8: what may be reported on the site, active categories and types with the visibility of this site;
     * with an equipment, only the types that fit its family. Needs {@code issue-type:read} on the site.
     */
    @Transactional(readOnly = true)
    public List<CategoryView> catalog(Perimeter perimeter, SiteId siteId, Optional<UUID> assetId) {
        SiteSummary site = site(siteId);
        perimeter.require(Permission.ISSUE_TYPE_READ, site.organizationId(), siteId);
        Optional<UUID> family = assetId.flatMap(assets::findAsset).filter(a -> a.siteId().equals(siteId))
                .map(AssetView::familyId);
        Map<UUID, IssueTypeConfig> configs = configsBySite(siteId);
        return categories(Optional.of(site.organizationId()), false).stream()
                .map(c -> new CategoryView(c.category(), c.types().stream()
                        .filter(t -> Optional.ofNullable(configs.get(t.id())).map(IssueTypeConfig::active).orElse(true))
                        .filter(t -> family.map(t::fits).orElse(true)).toList()))
                .filter(c -> !c.types().isEmpty()).toList();
    }

    /** The visibility of new tickets of the type on the site (H8), for the catalog of the site. */
    @Transactional(readOnly = true)
    public Visibility visibilityOnSite(SiteId siteId, IssueType type) {
        return visibilityOn(siteId, type);
    }

    /** The visibility of new tickets of the type on the site (H8). */
    Visibility visibilityOn(SiteId siteId, IssueType type) {
        return issueTypes.config(siteId, type.id()).map(IssueTypeConfig::visibility).orElse(type.defaultVisibility());
    }

    /** The setting of the type on the site, if any. */
    Optional<IssueTypeConfig> configOn(SiteId siteId, UUID typeId) {
        return issueTypes.config(siteId, typeId);
    }

    /** The categories and types of an organization; needs {@code issue-type:manage} in it. */
    @Transactional(readOnly = true)
    public List<CategoryView> forOrganization(Perimeter perimeter, OrganizationId organization,
                                              boolean includeArchived) {
        perimeter.requireInOrganization(Permission.ISSUE_TYPE_MANAGE, organization);
        return categories(Optional.of(organization), includeArchived);
    }

    /** The platform's categories and types; needs {@code reference-data:read}. */
    @Transactional(readOnly = true)
    public List<CategoryView> platform(Perimeter perimeter, boolean includeArchived) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_READ);
        return categories(Optional.empty(), includeArchived);
    }

    /**
     * Adds a category for an organization ({@code issue-type:manage} in it) or, without one, a platform default
     * ({@code reference-data:create}). {@code ISSUE_CATEGORY_CODE_TAKEN}.
     */
    @Transactional
    public CategoryView createCategory(Perimeter perimeter, Optional<OrganizationId> organization, String code,
                                       LocalizedText name, int sortOrder) {
        requireOwner(perimeter, organization, Permission.REFERENCE_DATA_CREATE);
        if (issueTypes.categoryCodeTaken(organization, code)) {
            throw new BusinessException("ISSUE_CATEGORY_CODE_TAKEN", ErrorCategory.CONFLICT, "Code used");
        }
        Instant now = clock.instant();
        IssueCategory category = issueTypes.save(IssueCategory.create(organization, code, name, sortOrder, now));
        events.publishEvent(new IssueTypeChangedV1(category.id(), "CATEGORY", code,
                organization.map(OrganizationId::value), true, perimeter.actor(), now));
        return new CategoryView(category, List.of());
    }

    /** Renames, reorders, archives a category with the permissions of its owner. {@code VERSION_CONFLICT}. */
    @Transactional
    public CategoryView updateCategory(Perimeter perimeter, UUID categoryId, LocalizedText name, int sortOrder,
                                       boolean active, long version) {
        IssueCategory category = issueTypes.category(categoryId).orElseThrow(Errors::notFound);
        requireOwner(perimeter, category.organizationId(), Permission.REFERENCE_DATA_UPDATE);
        if (category.active() != active && category.organizationId().isEmpty()) {
            perimeter.requireOnPlatform(Permission.REFERENCE_DATA_ARCHIVE);
        }
        Errors.checkVersion(version, category.version());
        Instant now = clock.instant();
        category.update(name, sortOrder, active, now);
        issueTypes.save(category);
        events.publishEvent(new IssueTypeChangedV1(category.id(), "CATEGORY", category.code(),
                category.organizationId().map(OrganizationId::value), active, perimeter.actor(), now));
        return new CategoryView(category, issueTypes.typesOf(List.of(categoryId), category.organizationId(), true));
    }

    /**
     * Adds a type to a category: of the category's owner, or of {@code organization} when it completes a platform
     * category. {@code ISSUE_TYPE_CODE_TAKEN}.
     */
    @Transactional
    public IssueType createType(Perimeter perimeter, UUID categoryId, Optional<OrganizationId> organization,
                                TypeCommand command) {
        IssueCategory category = issueTypes.category(categoryId).orElseThrow(Errors::notFound);
        if (category.organizationId().isPresent() && organization.isPresent()
                && !category.organizationId().equals(organization)) {
            throw Errors.notFound();
        }
        Optional<OrganizationId> owner = category.organizationId().or(() -> organization);
        requireOwner(perimeter, owner, Permission.REFERENCE_DATA_CREATE);
        if (issueTypes.typeCodeTaken(owner, command.code())) {
            throw new BusinessException("ISSUE_TYPE_CODE_TAKEN", ErrorCategory.CONFLICT, "Code used");
        }
        Instant now = clock.instant();
        IssueType type = issueTypes.save(IssueType.create(categoryId, owner, command.code(), details(command), now));
        events.publishEvent(new IssueTypeChangedV1(type.id(), "TYPE", type.code(), owner.map(OrganizationId::value),
                true, perimeter.actor(), now));
        return type;
    }

    /** Changes a type with the permissions of its owner. {@code VERSION_CONFLICT}. */
    @Transactional
    public IssueType updateType(Perimeter perimeter, UUID typeId, TypeCommand command, boolean active, long version) {
        IssueType type = issueTypes.type(typeId).orElseThrow(Errors::notFound);
        requireOwner(perimeter, type.organizationId(), Permission.REFERENCE_DATA_UPDATE);
        Errors.checkVersion(version, type.version());
        Instant now = clock.instant();
        type.update(details(command), active, now);
        issueTypes.save(type);
        events.publishEvent(new IssueTypeChangedV1(type.id(), "TYPE", type.code(),
                type.organizationId().map(OrganizationId::value), active, perimeter.actor(), now));
        return type;
    }

    /** H4, H8: the effective setting of every active type on the site; needs {@code issue-type:manage} there. */
    @Transactional(readOnly = true)
    public List<ConfigView> configs(Perimeter perimeter, SiteId siteId) {
        SiteSummary site = site(siteId);
        perimeter.require(Permission.ISSUE_TYPE_MANAGE, site.organizationId(), siteId);
        Map<UUID, IssueTypeConfig> configs = configsBySite(siteId);
        Map<PersonId, PersonSummary> people = people(configs.values().stream()
                .flatMap(c -> c.technicianIds().stream()).map(PersonId::new).distinct().toList());
        return categories(Optional.of(site.organizationId()), false).stream()
                .flatMap(c -> c.types().stream().map(t -> view(t, c.category(), configs.get(t.id()), people)))
                .toList();
    }

    /**
     * Sets the routing and visibility of a type on the site (H4, H8); needs {@code issue-type:manage}. Each designated
     * person must hold {@code ticket:update-status} on the site ({@code TECHNICIAN_NOT_ELIGIBLE} with their ids).
     * New tickets only. Audited.
     */
    @Transactional
    public ConfigView setConfig(Perimeter perimeter, SiteId siteId, UUID typeId, RoutingDestination routing,
                                List<UUID> technicians, Visibility visibility, boolean active, Optional<Long> version) {
        SiteSummary site = site(siteId);
        perimeter.require(Permission.ISSUE_TYPE_MANAGE, site.organizationId(), siteId);
        IssueType type = issueTypes.type(typeId).filter(t -> t.usableBy(site.organizationId()))
                .orElseThrow(Errors::notFound);
        List<String> refused = technicians.stream().distinct()
                .filter(t -> !perimeters.perimeterOf(new PersonId(t)).can(Permission.TICKET_UPDATE_STATUS,
                        site.organizationId(), siteId))
                .map(UUID::toString).toList();
        if (!refused.isEmpty()) {
            throw new BusinessException("TECHNICIAN_NOT_ELIGIBLE", ErrorCategory.RULE_VIOLATION, "Not eligible",
                    refused);
        }
        Instant now = clock.instant();
        Optional<IssueTypeConfig> current = issueTypes.config(siteId, typeId);
        String before = current.map(IssueTypeService::describe).orElse("");
        IssueTypeConfig config;
        if (current.isPresent()) {
            config = current.get();
            version.ifPresent(v -> Errors.checkVersion(v, config.version()));
            config.change(routing, technicians, visibility, active, now);
        } else {
            config = IssueTypeConfig.create(site.organizationId(), siteId, typeId, routing, technicians, visibility,
                    active, now);
        }
        issueTypes.save(config);
        events.publishEvent(new SiteIssueTypeConfigChangedV1(site.organizationId(), siteId, typeId, before,
                describe(config), perimeter.actor(), now));
        IssueCategory category = issueTypes.category(type.categoryId()).orElseThrow();
        return view(type, category, config, people(config.technicianIds().stream().map(PersonId::new).toList()));
    }

    /** People who may be designated for the routing on the site (H4); needs {@code issue-type:manage}. */
    @Transactional(readOnly = true)
    public List<PersonSummary> eligibleTechnicians(Perimeter perimeter, SiteId siteId) {
        SiteSummary site = site(siteId);
        perimeter.require(Permission.ISSUE_TYPE_MANAGE, site.organizationId(), siteId);
        List<PersonId> holders = perimeters.holders(Permission.TICKET_UPDATE_STATUS, site.organizationId(), siteId,
                false);
        return identity.findPersons(holders).stream()
                .sorted(Comparator.comparing(PersonSummary::lastName).thenComparing(PersonSummary::firstName))
                .toList();
    }

    /** The audited description of a setting (no personal data beyond identifiers). */
    private static String describe(IssueTypeConfig c) {
        return "routing=" + c.routing() + ";technicians=" + c.technicianIds() + ";visibility=" + c.visibility()
                + ";active=" + c.active();
    }

    /** The effective setting of a type. */
    private static ConfigView view(IssueType type, IssueCategory category, IssueTypeConfig config,
                                   Map<PersonId, PersonSummary> people) {
        if (config == null) {
            return new ConfigView(type, category, false, RoutingDestination.SITE_MANAGER, List.of(),
                    type.defaultVisibility(), true, Optional.empty());
        }
        return new ConfigView(type, category, true, config.routing(), config.technicianIds().stream()
                .map(id -> people.get(new PersonId(id))).filter(java.util.Objects::nonNull).toList(),
                config.visibility(), config.active(), Optional.of(config.version()));
    }

    /** People by identifier. */
    private Map<PersonId, PersonSummary> people(List<PersonId> ids) {
        return ids.isEmpty() ? Map.of() : identity.findPersons(ids).stream()
                .collect(Collectors.toMap(PersonSummary::id, Function.identity()));
    }

    /** The settings of a site by type. */
    private Map<UUID, IssueTypeConfig> configsBySite(SiteId siteId) {
        return issueTypes.configs(siteId).stream()
                .collect(Collectors.toMap(IssueTypeConfig::issueTypeId, Function.identity()));
    }

    /** Categories with their types visible to the organization (platform only when empty). */
    private List<CategoryView> categories(Optional<OrganizationId> organization, boolean includeArchived) {
        List<IssueCategory> categories = issueTypes.categories(organization, includeArchived);
        Map<UUID, List<IssueType>> types = issueTypes.typesOf(categories.stream().map(IssueCategory::id).toList(),
                organization, includeArchived).stream().collect(Collectors.groupingBy(IssueType::categoryId));
        return categories.stream().map(c -> new CategoryView(c, types.getOrDefault(c.id(), List.of()))).toList();
    }

    /** The platform permission for a platform item, {@code issue-type:manage} in the organization otherwise. */
    private static void requireOwner(Perimeter perimeter, Optional<OrganizationId> owner, Permission platform) {
        owner.ifPresentOrElse(o -> perimeter.requireInOrganization(Permission.ISSUE_TYPE_MANAGE, o),
                () -> perimeter.requireOnPlatform(platform));
    }

    /** The details of a type typed by a person. */
    private static IssueType.Details details(TypeCommand command) {
        return new IssueType.Details(command.name(), command.assetRequired(), command.defaultVisibility(),
                command.assetFamilyIds() == null ? List.of() : command.assetFamilyIds(), command.sortOrder());
    }

    /** The site, or {@code NOT_FOUND}. */
    private SiteSummary site(SiteId siteId) {
        return sites.findSite(siteId).orElseThrow(Errors::notFound);
    }
}
