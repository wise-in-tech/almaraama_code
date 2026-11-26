package com.almaraama.assets.adapters.in.web;

import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetFamilyInput;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetFamilyResponse;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetModelInput;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetModelResponse;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AttributeDefinitionInput;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AttributeDefinitionResponse;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AttributeOptionInput;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.SupplierInput;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.SupplierResponse;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.UpdateAssetFamilyRequest;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.UpdateAssetModelRequest;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.UpdateAttributeDefinitionRequest;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.UpdateSupplierRequest;
import com.almaraama.assets.application.FamilyService;
import com.almaraama.assets.application.FamilyService.FamilyView;
import com.almaraama.assets.application.SupplierService;
import com.almaraama.assets.application.SupplierService.SupplierCommand;
import com.almaraama.customization.api.AttributesApi.DefinitionChange;
import com.almaraama.customization.api.AttributesApi.NewDefinition;
import com.almaraama.customization.api.AttributesApi.OptionInput;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.perimeter.Perimeter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Equipment families, models, characteristics and suppliers (G1, G3, M7, ADR-039). */
@RestController
class FamilyController {

    private final FamilyService families;
    private final SupplierService suppliers;

    /**
     * @param families  families, models, characteristics
     * @param suppliers supplier contacts
     */
    FamilyController(FamilyService families, SupplierService suppliers) {
        this.families = families;
        this.suppliers = suppliers;
    }

    /** {@code GET /v1/organizations/{id}/asset-families}: see {@link FamilyService#usable}. */
    @GetMapping("/v1/organizations/{organizationId}/asset-families")
    List<AssetFamilyResponse> list(Perimeter perimeter, @PathVariable UUID organizationId,
                                   @RequestParam(defaultValue = "false") boolean includeArchived) {
        return families.usable(perimeter, new OrganizationId(organizationId), includeArchived).stream()
                .map(AssetFamilyResponse::of).toList();
    }

    /** {@code POST /v1/organizations/{id}/asset-families}: see {@link FamilyService#create}. */
    @PostMapping("/v1/organizations/{organizationId}/asset-families")
    @ResponseStatus(HttpStatus.CREATED)
    AssetFamilyResponse create(Perimeter perimeter, @PathVariable UUID organizationId,
                               @Valid @RequestBody AssetFamilyInput body) {
        return AssetFamilyResponse.of(families.create(perimeter, Optional.of(new OrganizationId(organizationId)),
                body.code(), body.name().toText(), body.sortOrder() == null ? 100 : body.sortOrder()));
    }

    /** {@code GET /v1/admin/asset-families}: see {@link FamilyService#platform}. */
    @GetMapping("/v1/admin/asset-families")
    List<AssetFamilyResponse> platform(Perimeter perimeter,
                                       @RequestParam(defaultValue = "false") boolean includeArchived) {
        return families.platform(perimeter, includeArchived).stream().map(AssetFamilyResponse::of).toList();
    }

    /** {@code POST /v1/admin/asset-families}: a platform default, see {@link FamilyService#create}. */
    @PostMapping("/v1/admin/asset-families")
    @ResponseStatus(HttpStatus.CREATED)
    AssetFamilyResponse createPlatform(Perimeter perimeter, @Valid @RequestBody AssetFamilyInput body) {
        return AssetFamilyResponse.of(families.create(perimeter, Optional.empty(), body.code(), body.name().toText(),
                body.sortOrder() == null ? 100 : body.sortOrder()));
    }

    /** {@code PUT /v1/asset-families/{familyId}}: see {@link FamilyService#update}. */
    @PutMapping("/v1/asset-families/{familyId}")
    AssetFamilyResponse update(Perimeter perimeter, @PathVariable UUID familyId,
                               @Valid @RequestBody UpdateAssetFamilyRequest body) {
        FamilyView view = families.update(perimeter, familyId, body.name().toText(), body.sortOrder(), body.active(),
                body.version());
        return AssetFamilyResponse.of(view);
    }

    /** {@code POST /v1/asset-families/{familyId}/models}: see {@link FamilyService#addModel}. */
    @PostMapping("/v1/asset-families/{familyId}/models")
    @ResponseStatus(HttpStatus.CREATED)
    AssetModelResponse addModel(Perimeter perimeter, @PathVariable UUID familyId,
                                @RequestParam(required = false) UUID organizationId,
                                @Valid @RequestBody AssetModelInput body) {
        return AssetModelResponse.of(families.addModel(perimeter, familyId,
                Optional.ofNullable(organizationId).map(OrganizationId::new), body.name(), body.modelVersion(),
                body.manufacturer()));
    }

    /** {@code PUT /v1/asset-models/{modelId}}: see {@link FamilyService#updateModel}. */
    @PutMapping("/v1/asset-models/{modelId}")
    AssetModelResponse updateModel(Perimeter perimeter, @PathVariable UUID modelId,
                                   @Valid @RequestBody UpdateAssetModelRequest body) {
        return AssetModelResponse.of(families.updateModel(perimeter, modelId, body.name(), body.modelVersion(),
                body.manufacturer(), body.active(), body.version()));
    }

    /** {@code GET /v1/asset-families/{familyId}/attributes}: see {@link FamilyService#attributes}. */
    @GetMapping("/v1/asset-families/{familyId}/attributes")
    List<AttributeDefinitionResponse> attributes(Perimeter perimeter, @PathVariable UUID familyId,
                                                 @RequestParam(required = false) UUID organizationId,
                                                 @RequestParam(defaultValue = "false") boolean includeArchived) {
        return families.attributes(perimeter, familyId, Optional.ofNullable(organizationId).map(OrganizationId::new),
                includeArchived).stream().map(AttributeDefinitionResponse::of).toList();
    }

    /** {@code POST /v1/asset-families/{familyId}/attributes}: see {@link FamilyService#addAttribute}. */
    @PostMapping("/v1/asset-families/{familyId}/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    AttributeDefinitionResponse addAttribute(Perimeter perimeter, @PathVariable UUID familyId,
                                             @Valid @RequestBody AttributeDefinitionInput body) {
        return AttributeDefinitionResponse.of(families.addAttribute(perimeter, familyId, new NewDefinition(
                body.scope(), Optional.ofNullable(body.organizationId()), familyId, body.code(), body.dataType(),
                Boolean.TRUE.equals(body.required()), body.label().toText(), Optional.ofNullable(body.unit()),
                options(body.options()), body.sortOrder() == null ? 100 : body.sortOrder())));
    }

    /** {@code PUT /v1/attribute-definitions/{definitionId}}: see {@link FamilyService#updateAttribute}. */
    @PutMapping("/v1/attribute-definitions/{definitionId}")
    AttributeDefinitionResponse updateAttribute(Perimeter perimeter, @PathVariable UUID definitionId,
                                                @Valid @RequestBody UpdateAttributeDefinitionRequest body) {
        return AttributeDefinitionResponse.of(families.updateAttribute(perimeter, definitionId, new DefinitionChange(
                body.required(), body.label().toText(), Optional.ofNullable(body.unit()), options(body.options()),
                body.sortOrder(), body.active(), body.version())));
    }

    /** {@code GET /v1/organizations/{id}/suppliers}: see {@link SupplierService#list}. */
    @GetMapping("/v1/organizations/{organizationId}/suppliers")
    List<SupplierResponse> suppliers(Perimeter perimeter, @PathVariable UUID organizationId,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "false") boolean includeInactive) {
        return suppliers.list(perimeter, new OrganizationId(organizationId), q, includeInactive).stream()
                .map(SupplierResponse::of).toList();
    }

    /** {@code POST /v1/organizations/{id}/suppliers}: see {@link SupplierService#create}. */
    @PostMapping("/v1/organizations/{organizationId}/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    SupplierResponse createSupplier(Perimeter perimeter, @PathVariable UUID organizationId,
                                    @Valid @RequestBody SupplierInput body) {
        return SupplierResponse.of(suppliers.create(perimeter, new OrganizationId(organizationId),
                new SupplierCommand(body.name(), body.phone(), body.email(), body.activity())));
    }

    /** {@code PUT /v1/suppliers/{supplierId}}: see {@link SupplierService#update}. */
    @PutMapping("/v1/suppliers/{supplierId}")
    SupplierResponse updateSupplier(Perimeter perimeter, @PathVariable UUID supplierId,
                                    @Valid @RequestBody UpdateSupplierRequest body) {
        return SupplierResponse.of(suppliers.update(perimeter, supplierId, new SupplierCommand(body.name(),
                body.phone(), body.email(), body.activity()), body.active(), body.version()));
    }

    /** The options typed, as the customization API takes them. */
    private static List<OptionInput> options(List<AttributeOptionInput> options) {
        return options == null ? List.of() : options.stream().map(AttributeOptionInput::toOption).toList();
    }
}
