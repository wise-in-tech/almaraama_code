package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.IssueCategory;
import com.almaraama.maintenance.domain.IssueType;
import com.almaraama.maintenance.domain.IssueTypeConfig;
import com.almaraama.maintenance.domain.MaintenanceRepositories.IssueTypes;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link IssueTypes} on the issue tables. */
@Component
class JpaIssueTypes implements IssueTypes {

    private final SpringDataIssueCategories categories;
    private final SpringDataIssueTypes types;
    private final SpringDataIssueTypeConfigs configs;

    /**
     * @param categories categories
     * @param types      types
     * @param configs    settings per site
     */
    JpaIssueTypes(SpringDataIssueCategories categories, SpringDataIssueTypes types,
                  SpringDataIssueTypeConfigs configs) {
        this.categories = categories;
        this.types = types;
        this.configs = configs;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<IssueCategory> category(UUID id) {
        return categories.findById(id);
    }

    /** {@inheritDoc} */
    @Override
    public List<IssueCategory> categories(Optional<OrganizationId> organization, boolean includeArchived) {
        return organization.map(o -> categories.usable(o.value(), includeArchived))
                .orElseGet(() -> categories.platform(includeArchived));
    }

    /** {@inheritDoc} */
    @Override
    public IssueCategory save(IssueCategory category) {
        return categories.save(category);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<IssueType> type(UUID id) {
        return types.findById(id);
    }

    /** {@inheritDoc} */
    @Override
    public List<IssueType> types(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : types.findByIdIn(ids);
    }

    /** {@inheritDoc} */
    @Override
    public List<IssueType> typesOf(Collection<UUID> categoryIds, Optional<OrganizationId> organization,
                                   boolean includeArchived) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return organization.map(o -> types.usable(categoryIds, o.value(), includeArchived))
                .orElseGet(() -> types.platform(categoryIds, includeArchived));
    }

    /** {@inheritDoc} */
    @Override
    public boolean categoryCodeTaken(Optional<OrganizationId> organization, String code) {
        return categories(organization, true).stream().anyMatch(c -> c.code().equals(code));
    }

    /** {@inheritDoc} */
    @Override
    public boolean typeCodeTaken(Optional<OrganizationId> organization, String code) {
        return organization.map(o -> types.codeTaken(o.value(), code)).orElseGet(() -> types.platformCodeTaken(code));
    }

    /** {@inheritDoc} */
    @Override
    public IssueType save(IssueType type) {
        return types.save(type);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<IssueTypeConfig> config(SiteId site, UUID issueTypeId) {
        return configs.findBySiteIdAndIssueTypeId(site.value(), issueTypeId);
    }

    /** {@inheritDoc} */
    @Override
    public List<IssueTypeConfig> configs(SiteId site) {
        return configs.findBySiteId(site.value());
    }

    /** {@inheritDoc} */
    @Override
    public IssueTypeConfig save(IssueTypeConfig config) {
        return configs.save(config);
    }
}
