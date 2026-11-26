package com.almaraama.assets.application;

import com.almaraama.assets.domain.AssetFamily;
import com.almaraama.assets.domain.AssetModel;
import com.almaraama.assets.domain.AssetRepositories.Families;
import com.almaraama.customization.api.AttributeDefinitionView;
import com.almaraama.customization.api.AttributesApi;
import com.almaraama.customization.api.AttributesApi.DefinitionChange;
import com.almaraama.customization.api.AttributesApi.NewDefinition;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.perimeter.Perimeter;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Equipment families, models and their characteristics (G1, G3, M7, ADR-039). Platform defaults are kept by the
 * administrators ({@code reference-data:*}); an organization completes them with {@code asset:create} or {@code
 * asset:update} on itself or on one of its sites.
 */
@Service
public class FamilyService {

    private static final Logger LOG = LoggerFactory.getLogger(FamilyService.class);

    /** A family with the models the reader may use. */
    public record FamilyView(AssetFamily family, List<AssetModel> models) {
    }

    private final Families families;
    private final AttributesApi attributes;
    private final Clock clock;

    /**
     * @param families   families and models
     * @param attributes characteristic definitions (customization module)
     * @param clock      time of changes
     */
    FamilyService(Families families, AttributesApi attributes, Clock clock) {
        this.families = families;
        this.attributes = attributes;
        this.clock = clock;
    }

    /** The families usable by an organization with their models; needs {@code asset:read} in it. */
    @Transactional(readOnly = true)
    public List<FamilyView> usable(Perimeter perimeter, OrganizationId organization, boolean includeArchived) {
        perimeter.requireInOrganization(Permission.ASSET_READ, organization);
        return views(families.usable(Optional.of(organization), includeArchived), Optional.of(organization),
                includeArchived);
    }

    /** The platform's families; needs {@code reference-data:read}. */
    @Transactional(readOnly = true)
    public List<FamilyView> platform(Perimeter perimeter, boolean includeArchived) {
        perimeter.requireOnPlatform(Permission.REFERENCE_DATA_READ);
        return views(families.usable(Optional.empty(), includeArchived), Optional.empty(), includeArchived);
    }

    /**
     * Adds a family for the organization ({@code asset:create} in it) or, with an empty organization, a platform
     * default ({@code reference-data:create}). {@code ASSET_FAMILY_CODE_TAKEN}, {@code ASSET_FAMILY_CODE_INVALID},
     * {@code ASSET_FAMILY_NAME_INVALID}.
     */
    @Transactional
    public FamilyView create(Perimeter perimeter, Optional<OrganizationId> organization, String code,
                             LocalizedText name, int sortOrder) {
        organization.ifPresentOrElse(o -> perimeter.requireInOrganization(Permission.ASSET_CREATE, o),
                () -> perimeter.requireOnPlatform(Permission.REFERENCE_DATA_CREATE));
        if (families.codeTaken(organization, code)) {
            throw new BusinessException("ASSET_FAMILY_CODE_TAKEN", ErrorCategory.CONFLICT, "Code used");
        }
        AssetFamily family = families.save(AssetFamily.create(organization, code, name, sortOrder, clock.instant()));
        LOG.debug("Equipment family {} created for {}", code, organization.map(o -> o.value().toString())
                .orElse("the platform"));
        return new FamilyView(family, List.of());
    }

    /**
     * Renames, reorders, archives a family: {@code reference-data:update} (and {@code archive}) for a platform family,
     * {@code asset:update} (and {@code asset:archive}) in the organization for its own. {@code VERSION_CONFLICT}.
     */
    @Transactional
    public FamilyView update(Perimeter perimeter, UUID familyId, LocalizedText name, int sortOrder, boolean active,
                             long version) {
        AssetFamily family = families.findById(familyId).orElseThrow(Errors::notFound);
        requireOwner(perimeter, family.organizationId(), Permission.ASSET_UPDATE, Permission.REFERENCE_DATA_UPDATE);
        if (family.active() != active) {
            requireOwner(perimeter, family.organizationId(), Permission.ASSET_ARCHIVE,
                    Permission.REFERENCE_DATA_ARCHIVE);
        }
        Errors.checkVersion(version, family.version());
        family.update(name, sortOrder, active, clock.instant());
        AssetFamily saved = families.save(family);
        return views(List.of(saved), family.organizationId(), true).getFirst();
    }

    /**
     * Adds a model to a family. A platform family receives a platform model ({@code reference-data:create}) or, with
     * {@code organization}, the organization's own model ({@code asset:create}); an organization's family receives its
     * model. {@code ASSET_MODEL_TAKEN}, {@code ASSET_MODEL_INVALID}.
     */
    @Transactional
    public AssetModel addModel(Perimeter perimeter, UUID familyId, Optional<OrganizationId> organization, String name,
                               String modelVersion, String manufacturer) {
        AssetFamily family = families.findById(familyId).orElseThrow(Errors::notFound);
        Optional<OrganizationId> owner = family.organizationId().isPresent() ? family.organizationId() : organization;
        if (family.organizationId().isPresent() && organization.isPresent()
                && !organization.equals(family.organizationId())) {
            throw Errors.notFound();
        }
        requireOwner(perimeter, owner, Permission.ASSET_CREATE, Permission.REFERENCE_DATA_CREATE);
        boolean taken = families.modelsOf(List.of(familyId), owner, true).stream()
                .anyMatch(m -> m.name().equalsIgnoreCase(name == null ? "" : name.strip())
                        && m.modelVersion().orElse("").equalsIgnoreCase(modelVersion == null ? "" : modelVersion.strip()));
        if (taken) {
            throw new BusinessException("ASSET_MODEL_TAKEN", ErrorCategory.CONFLICT, "Model exists");
        }
        return families.save(AssetModel.create(familyId, owner, name, modelVersion, manufacturer, clock.instant()));
    }

    /** Renames or archives a model, with the permissions of its owner. {@code VERSION_CONFLICT}. */
    @Transactional
    public AssetModel updateModel(Perimeter perimeter, UUID modelId, String name, String modelVersion,
                                  String manufacturer, boolean active, long version) {
        AssetModel model = families.findModel(modelId).orElseThrow(Errors::notFound);
        requireOwner(perimeter, model.organizationId(), Permission.ASSET_UPDATE, Permission.REFERENCE_DATA_UPDATE);
        Errors.checkVersion(version, model.version());
        model.update(name, modelVersion, manufacturer, active, clock.instant());
        return families.save(model);
    }

    /**
     * The characteristics of a family: with an organization ({@code asset:read} in it) the platform's and its own;
     * without, the platform's ({@code reference-data:read}).
     */
    @Transactional(readOnly = true)
    public List<AttributeDefinitionView> attributes(Perimeter perimeter, UUID familyId,
                                                    Optional<OrganizationId> organization, boolean includeArchived) {
        AssetFamily family = families.findById(familyId).orElseThrow(Errors::notFound);
        organization.ifPresentOrElse(o -> {
            perimeter.requireInOrganization(Permission.ASSET_READ, o);
            if (!family.usableBy(o)) {
                throw Errors.notFound();
            }
        }, () -> perimeter.requireOnPlatform(Permission.REFERENCE_DATA_READ));
        return attributes.definitions(familyId, organization.map(OrganizationId::value), includeArchived);
    }

    /**
     * Defines a characteristic: scope {@code PLATFORM} on a platform family ({@code reference-data:create}), scope
     * {@code ORGANIZATION} on a family the organization may use ({@code asset:update} in it).
     */
    @Transactional
    public AttributeDefinitionView addAttribute(Perimeter perimeter, UUID familyId, NewDefinition definition) {
        AssetFamily family = families.findById(familyId).orElseThrow(Errors::notFound);
        if (definition.scope() == ScopeType.PLATFORM) {
            perimeter.requireOnPlatform(Permission.REFERENCE_DATA_CREATE);
            if (family.organizationId().isPresent()) {
                throw Errors.rule("ATTRIBUTE_SCOPE_INVALID");
            }
        } else {
            OrganizationId organization = definition.organizationId().map(OrganizationId::new)
                    .orElseThrow(() -> Errors.rule("ATTRIBUTE_SCOPE_INVALID"));
            perimeter.requireInOrganization(Permission.ASSET_UPDATE, organization);
            if (!family.usableBy(organization)) {
                throw Errors.notFound();
            }
        }
        return attributes.create(new NewDefinition(definition.scope(), definition.organizationId(), familyId,
                definition.code(), definition.dataType(), definition.required(), definition.label(),
                definition.unit(), definition.options(), definition.sortOrder()));
    }

    /** Changes a characteristic, with the permissions of its scope. */
    @Transactional
    public AttributeDefinitionView updateAttribute(Perimeter perimeter, UUID definitionId, DefinitionChange change) {
        AttributeDefinitionView definition = attributes.find(definitionId).orElseThrow(Errors::notFound);
        requireOwner(perimeter, definition.organizationId().map(OrganizationId::new), Permission.ASSET_UPDATE,
                Permission.REFERENCE_DATA_UPDATE);
        return attributes.update(definitionId, change);
    }

    /** The platform permission for a platform item, the organization permission for an organization's item. */
    private static void requireOwner(Perimeter perimeter, Optional<OrganizationId> owner, Permission organizationWide,
                                     Permission platform) {
        owner.ifPresentOrElse(o -> perimeter.requireInOrganization(organizationWide, o),
                () -> perimeter.requireOnPlatform(platform));
    }

    /** Families with the models visible to the organization, in one query for the models. */
    private List<FamilyView> views(List<AssetFamily> list, Optional<OrganizationId> organization,
                                   boolean includeArchived) {
        Map<UUID, List<AssetModel>> models = families.modelsOf(list.stream().map(AssetFamily::id).toList(),
                organization, includeArchived).stream().collect(Collectors.groupingBy(AssetModel::familyId));
        return list.stream().map(f -> new FamilyView(f, models.getOrDefault(f.id(), List.of()))).toList();
    }
}
