package com.almaraama.assets.adapters.in.web;

import com.almaraama.assets.application.AssetImportService;
import com.almaraama.assets.application.AssetService.AssetCard;
import com.almaraama.assets.application.AssetService.AssetDetail;
import com.almaraama.assets.application.AssetService.DocumentView;
import com.almaraama.assets.application.FamilyService.FamilyView;
import com.almaraama.assets.domain.Asset;
import com.almaraama.assets.domain.AssetDocumentType;
import com.almaraama.assets.domain.AssetModel;
import com.almaraama.assets.domain.AssetState;
import com.almaraama.assets.domain.Supplier;
import com.almaraama.assets.domain.Warranty;
import com.almaraama.customization.api.AttributeDataType;
import com.almaraama.customization.api.AttributeDefinitionView;
import com.almaraama.customization.api.AttributesApi.OptionInput;
import com.almaraama.files.api.SignedLink;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.paging.PageResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the equipment endpoints (contract tag {@code assets}). */
final class AssetWebDtos {

    private AssetWebDtos() {
    }

    // ---------------------------------------------------------------------------------------- shared shapes

    /** A text in one or more languages (contract {@code LocalizedTextInput}). */
    record LocalizedTextInput(@Size(min = 1, max = 100) String ar, @Size(min = 1, max = 100) String en,
                              @Size(min = 1, max = 100) String fr) {
        /** To the shared value type. */
        LocalizedText toText() {
            return new LocalizedText(ar, en, fr);
        }
    }

    /** A text in every language (contract {@code LocalizedText}). */
    record LocalizedTextResponse(String ar, String en, String fr) {
        /** From the shared value type, with the M6 fallback applied by the caller. */
        static LocalizedTextResponse of(LocalizedText text) {
            return new LocalizedTextResponse(text.ar(), text.en(), text.fr());
        }
    }

    /** An identifier and a name (contract {@code NamedRef}). */
    record NamedRef(UUID id, String name) {
    }

    /** A page (contract {@code PageMeta}). */
    record PageMeta(int page, int size, long totalElements, int totalPages) {
        /** From a page of results. */
        static PageMeta of(PageResult<?> result) {
            return new PageMeta(result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }

    /** Body with only the version read (contract {@code VersionOnly}). */
    record VersionOnly(@NotNull @Min(0) Long version) {
    }

    // ---------------------------------------------------------------------------------- families and models

    /** A family with its models (contract {@code AssetFamily}). */
    record AssetFamilyResponse(UUID id, ScopeType scope, String code, LocalizedTextResponse name, int sortOrder,
                               boolean active, List<AssetModelResponse> models, long version) {
        /** From a family and its models. */
        static AssetFamilyResponse of(FamilyView view) {
            return new AssetFamilyResponse(view.family().id(), view.family().ownerScope(), view.family().code(),
                    LocalizedTextResponse.of(view.family().name()), view.family().sortOrder(), view.family().active(),
                    view.models().stream().map(AssetModelResponse::of).toList(), view.family().version());
        }
    }

    /** Body of a new family (contract {@code AssetFamilyInput}). */
    record AssetFamilyInput(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,29}$") String code,
                            @NotNull @Valid LocalizedTextInput name, @Min(0) @Max(1000) Integer sortOrder) {
    }

    /** Body of a family change (contract {@code UpdateAssetFamilyRequest}). */
    record UpdateAssetFamilyRequest(@NotNull @Valid LocalizedTextInput name, @NotNull @Min(0) @Max(1000) Integer sortOrder,
                                    @NotNull Boolean active, @NotNull @Min(0) Long version) {
    }

    /** A model (contract {@code AssetModel}). */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    record AssetModelResponse(UUID id, UUID familyId, ScopeType scope, String name, String modelVersion,
                              String manufacturer, boolean active, long version) {
        /** From the model. */
        static AssetModelResponse of(AssetModel m) {
            return new AssetModelResponse(m.id(), m.familyId(), m.ownerScope(), m.name(),
                    m.modelVersion().orElse(null), m.manufacturer().orElse(null), m.active(), m.version());
        }
    }

    /** Body of a new model (contract {@code AssetModelInput}). */
    record AssetModelInput(@NotBlank @Size(max = 100) String name, @Size(max = 50) String modelVersion,
                           @Size(max = 100) String manufacturer) {
    }

    /** Body of a model change (contract {@code UpdateAssetModelRequest}). */
    record UpdateAssetModelRequest(@NotBlank @Size(max = 100) String name, @Size(max = 50) String modelVersion,
                                   @Size(max = 100) String manufacturer, @NotNull Boolean active,
                                   @NotNull @Min(0) Long version) {
    }

    // -------------------------------------------------------------------------------------- characteristics

    /** A choice of a list (contract {@code AttributeOption}). */
    record AttributeOptionResponse(String code, LocalizedTextResponse label, boolean active) {
    }

    /** A choice typed by a person (contract {@code AttributeOptionInput}). */
    record AttributeOptionInput(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,29}$") String code,
                                @NotNull @Valid LocalizedTextInput label, Boolean active) {
        /** To the customization API. */
        OptionInput toOption() {
            return new OptionInput(code, label.toText(), active == null || active);
        }
    }

    /** A characteristic (contract {@code AttributeDefinition}). */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    record AttributeDefinitionResponse(UUID id, ScopeType scope, UUID familyId, String code,
                                       AttributeDataType dataType, boolean required, LocalizedTextResponse label,
                                       String unit, List<AttributeOptionResponse> options, int sortOrder,
                                       boolean active, long version) {
        /** From the customization view. */
        static AttributeDefinitionResponse of(AttributeDefinitionView d) {
            return new AttributeDefinitionResponse(d.id(), d.scope(), d.familyId(), d.code(), d.dataType(),
                    d.required(), LocalizedTextResponse.of(d.label()), d.unit().orElse(null),
                    d.options().stream().map(o -> new AttributeOptionResponse(o.code(),
                            LocalizedTextResponse.of(o.label()), o.active())).toList(), d.sortOrder(), d.active(),
                    d.version());
        }
    }

    /** Body of a new characteristic (contract {@code AttributeDefinitionInput}). */
    record AttributeDefinitionInput(@NotNull ScopeType scope, UUID organizationId,
                                    @NotBlank @Pattern(regexp = "^[a-z][a-zA-Z0-9]{0,39}$") String code,
                                    @NotNull AttributeDataType dataType, Boolean required,
                                    @NotNull @Valid LocalizedTextInput label, @Size(max = 10) String unit,
                                    @Size(max = 50) List<@Valid AttributeOptionInput> options,
                                    @Min(0) @Max(1000) Integer sortOrder) {
    }

    /** Body of a characteristic change (contract {@code UpdateAttributeDefinitionRequest}). */
    record UpdateAttributeDefinitionRequest(@NotNull Boolean required, @NotNull @Valid LocalizedTextInput label,
                                            @Size(max = 10) String unit,
                                            @Size(max = 50) List<@Valid AttributeOptionInput> options,
                                            @NotNull @Min(0) @Max(1000) Integer sortOrder, @NotNull Boolean active,
                                            @NotNull @Min(0) Long version) {
    }

    // -------------------------------------------------------------------------------------------- suppliers

    /** A supplier (contract {@code Supplier}). */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    record SupplierResponse(UUID id, UUID organizationId, String name, String phone, String email, String activity,
                            boolean active, long version) {
        /** From the supplier. */
        static SupplierResponse of(Supplier s) {
            return new SupplierResponse(s.id(), s.organizationId().value(), s.name(), s.phone().orElse(null),
                    s.email().orElse(null), s.activity().orElse(null), s.active(), s.version());
        }
    }

    /** Body of a new supplier (contract {@code SupplierInput}). */
    record SupplierInput(@NotBlank @Size(max = 120) String name, @Size(max = 30) String phone,
                         @Size(max = 254) String email, @Size(max = 120) String activity) {
    }

    /** Body of a supplier change (contract {@code UpdateSupplierRequest}). */
    record UpdateSupplierRequest(@NotBlank @Size(max = 120) String name, @Size(max = 30) String phone,
                                 @Size(max = 254) String email, @Size(max = 120) String activity,
                                 @NotNull Boolean active, @NotNull @Min(0) Long version) {
    }

    // -------------------------------------------------------------------------------------------- equipment

    /** A family as shown on an equipment (contract {@code AssetFamilyRef}). */
    record AssetFamilyRef(UUID id, String code, LocalizedTextResponse name) {
    }

    /** Resident view of an equipment (contract {@code AssetSummary}). */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    record AssetSummaryResponse(UUID id, UUID siteId, String siteName, AssetFamilyRef family, NamedRef model,
                                String name, String reference, NamedRef building, Integer floor,
                                NamedRef outdoorArea, AssetState state, boolean archived, String qrCode) {
        /** From a card; the QR code only in the management view. */
        static AssetSummaryResponse of(AssetCard card) {
            Asset a = card.asset();
            return new AssetSummaryResponse(a.id(), a.siteId().value(), card.siteName(),
                    new AssetFamilyRef(card.family().id(), card.family().code(),
                            LocalizedTextResponse.of(card.family().name())),
                    card.model().map(m -> new NamedRef(m.id(), m.name())).orElse(null), a.name(), a.reference(),
                    card.building().map(b -> new NamedRef(b.id().value(), b.name())).orElse(null),
                    a.location().floor().orElse(null),
                    card.outdoorArea().map(o -> new NamedRef(o.id(), o.name())).orElse(null), a.state(),
                    a.archived(), card.management() ? a.qrCode() : null);
        }
    }

    /** A signed link (contract {@code SignedFileLink}). */
    record SignedFileLink(UUID fileId, String url, String contentType, Instant expiresAt) {
        /** From the files API. */
        static SignedFileLink of(SignedLink link) {
            return new SignedFileLink(link.fileId(), link.url(), link.contentType(), link.expiresAt());
        }
    }

    /** A document (contract {@code AssetDocument}). */
    record AssetDocumentResponse(UUID id, AssetDocumentType type, String title, SignedFileLink file,
                                 Instant addedAt) {
        /** From a document and its link. */
        static AssetDocumentResponse of(DocumentView view) {
            return new AssetDocumentResponse(view.document().id(), view.document().type(), view.document().title(),
                    SignedFileLink.of(view.link()), view.document().addedAt());
        }
    }

    /** A warranty (contract {@code Warranty}). */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    record WarrantyResponse(UUID id, LocalDate startDate, LocalDate endDate, int alertDaysBefore, UUID documentId,
                            String note, boolean active, long version) {
        /** From the warranty. */
        static WarrantyResponse of(Warranty w) {
            return new WarrantyResponse(w.id(), w.startDate(), w.endDate(), w.alertDaysBefore(),
                    w.documentId().orElse(null), w.note().orElse(null), w.active(), w.version());
        }
    }

    /** Management view (contract {@code AssetManagement}). */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    record AssetManagementResponse(String serialNumber, LocalDate commissionedOn, SupplierResponse supplier,
                                   Map<String, Object> attributes, List<AssetDocumentResponse> documents,
                                   List<WarrantyResponse> warranties) {
    }

    /** The whole equipment (contract {@code Asset}). */
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    record AssetResponse(UUID id, UUID siteId, String siteName, AssetFamilyRef family, NamedRef model, String name,
                         String reference, NamedRef building, Integer floor, NamedRef outdoorArea, AssetState state,
                         boolean archived, String qrCode, AssetManagementResponse management, long version) {
        /** From the detail; {@code management} only with {@code asset:update}. */
        static AssetResponse of(AssetDetail detail) {
            AssetSummaryResponse s = AssetSummaryResponse.of(detail.card());
            Asset a = detail.card().asset();
            AssetManagementResponse management = detail.card().management()
                    ? new AssetManagementResponse(a.serialNumber().orElse(null), a.commissionedOn().orElse(null),
                    detail.supplier().map(SupplierResponse::of).orElse(null), detail.attributes(),
                    detail.documents().stream().map(AssetDocumentResponse::of).toList(),
                    detail.warranties().stream().map(WarrantyResponse::of).toList())
                    : null;
            return new AssetResponse(s.id(), s.siteId(), s.siteName(), s.family(), s.model(), s.name(),
                    s.reference(), s.building(), s.floor(), s.outdoorArea(), s.state(), s.archived(), s.qrCode(),
                    management, a.version());
        }
    }

    /** A page of equipment (contract {@code AssetPage}). */
    record AssetPage(List<AssetSummaryResponse> items, PageMeta page) {
    }

    /** Body of a new equipment (contract {@code AssetInput}). */
    record AssetInput(@NotNull UUID familyId, UUID modelId, @NotBlank @Size(max = 120) String name,
                      @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_./-]{0,39}$") String reference, UUID buildingId,
                      @Min(-10) @Max(200) Integer floor, UUID outdoorAreaId, AssetState state,
                      @Size(max = 80) String serialNumber, LocalDate commissionedOn, UUID supplierId,
                      Map<String, Object> attributes) {
    }

    /** Body of an equipment change (contract {@code UpdateAssetRequest}). */
    record UpdateAssetRequest(UUID modelId, @NotBlank @Size(max = 120) String name,
                              @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_./-]{0,39}$") String reference,
                              UUID buildingId, @Min(-10) @Max(200) Integer floor, UUID outdoorAreaId,
                              @NotNull AssetState state, @Size(max = 80) String serialNumber,
                              LocalDate commissionedOn, UUID supplierId, @NotNull Map<String, Object> attributes,
                              @NotNull @Min(0) Long version) {
    }

    /** Body of a new document (contract {@code AssetDocumentInput}). */
    record AssetDocumentInput(@NotNull UUID fileId, @NotNull AssetDocumentType type,
                              @NotBlank @Size(max = 120) String title) {
    }

    /** Body of a new warranty (contract {@code WarrantyInput}). */
    record WarrantyInput(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                         @Min(0) @Max(365) Integer alertDaysBefore, UUID documentId, @Size(max = 300) String note) {
    }

    /** Body of a warranty change (contract {@code UpdateWarrantyRequest}). */
    record UpdateWarrantyRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                 @Min(0) @Max(365) Integer alertDaysBefore, UUID documentId,
                                 @Size(max = 300) String note, @NotNull Boolean active,
                                 @NotNull @Min(0) Long version) {
    }

    /** The import report (contract {@code AssetImportReport}). */
    record AssetImportReport(AssetImportService.Outcome outcome, int totalRows, int createdCount,
                             List<AssetImportErrorResponse> errors) {
        /** From the use case's report. */
        static AssetImportReport of(AssetImportService.Report report) {
            return new AssetImportReport(report.outcome(), report.totalRows(), report.createdCount(),
                    report.errors().stream().map(e -> new AssetImportErrorResponse(e.row(), e.column(), e.code(),
                            e.value())).toList());
        }
    }

    /** One error of the report (contract {@code AssetImportError}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AssetImportErrorResponse(int row, String column, String code, String value) {
    }
}
