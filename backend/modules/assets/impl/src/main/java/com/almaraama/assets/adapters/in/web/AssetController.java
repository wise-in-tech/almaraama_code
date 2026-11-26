package com.almaraama.assets.adapters.in.web;

import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetDocumentInput;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetImportReport;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetInput;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetPage;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetResponse;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.AssetSummaryResponse;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.PageMeta;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.UpdateAssetRequest;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.UpdateWarrantyRequest;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.VersionOnly;
import com.almaraama.assets.adapters.in.web.AssetWebDtos.WarrantyInput;
import com.almaraama.assets.application.AssetImportService;
import com.almaraama.assets.application.AssetService;
import com.almaraama.assets.application.AssetService.AssetCard;
import com.almaraama.assets.application.AssetService.AssetCommand;
import com.almaraama.assets.application.AssetService.ListFilter;
import com.almaraama.assets.application.AssetService.WarrantyCommand;
import com.almaraama.assets.domain.AssetState;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Equipment of the sites, their QR codes, documents, warranties and Excel import (G1 to G4, ADR-039). */
@RestController
class AssetController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AssetService assets;
    private final AssetImportService imports;

    /**
     * @param assets  equipment use cases
     * @param imports Excel import
     */
    AssetController(AssetService assets, AssetImportService imports) {
        this.assets = assets;
        this.imports = imports;
    }

    /** {@code GET /v1/sites/{siteId}/assets}: see {@link AssetService#list}. */
    @GetMapping("/v1/sites/{siteId}/assets")
    AssetPage list(Perimeter perimeter, @PathVariable UUID siteId, @RequestParam(required = false) UUID buildingId,
                   @RequestParam(required = false) UUID outdoorAreaId, @RequestParam(required = false) UUID familyId,
                   @RequestParam(required = false) AssetState state, @RequestParam(required = false) String q,
                   @RequestParam(defaultValue = "false") boolean includeArchived,
                   @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        PageResult<AssetCard> found = assets.list(perimeter, new SiteId(siteId),
                new ListFilter(buildingId, outdoorAreaId, familyId, state, q, includeArchived), PageQuery.of(page, size));
        return new AssetPage(found.items().stream().map(AssetSummaryResponse::of).toList(), PageMeta.of(found));
    }

    /** {@code POST /v1/sites/{siteId}/assets}: see {@link AssetService#create}. */
    @PostMapping("/v1/sites/{siteId}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    AssetResponse create(Perimeter perimeter, @PathVariable UUID siteId, @Valid @RequestBody AssetInput body) {
        return AssetResponse.of(assets.create(perimeter, new SiteId(siteId), new AssetCommand(body.familyId(),
                Optional.ofNullable(body.modelId()), body.name(), Optional.ofNullable(body.reference()),
                Optional.ofNullable(body.buildingId()), Optional.ofNullable(body.floor()),
                Optional.ofNullable(body.outdoorAreaId()), body.state(), body.serialNumber(),
                Optional.ofNullable(body.commissionedOn()), Optional.ofNullable(body.supplierId()),
                body.attributes() == null ? Map.of() : body.attributes())));
    }

    /** {@code GET /v1/assets/{assetId}}: see {@link AssetService#get}. */
    @GetMapping("/v1/assets/{assetId}")
    AssetResponse get(Perimeter perimeter, @PathVariable UUID assetId) {
        return AssetResponse.of(assets.get(perimeter, assetId));
    }

    /** {@code PUT /v1/assets/{assetId}}: see {@link AssetService#update}. */
    @PutMapping("/v1/assets/{assetId}")
    AssetResponse update(Perimeter perimeter, @PathVariable UUID assetId, @Valid @RequestBody UpdateAssetRequest body) {
        return AssetResponse.of(assets.update(perimeter, assetId, new AssetCommand(null,
                Optional.ofNullable(body.modelId()), body.name(), Optional.of(body.reference()),
                Optional.ofNullable(body.buildingId()), Optional.ofNullable(body.floor()),
                Optional.ofNullable(body.outdoorAreaId()), body.state(), body.serialNumber(),
                Optional.ofNullable(body.commissionedOn()), Optional.ofNullable(body.supplierId()),
                body.attributes()), body.version()));
    }

    /** {@code POST /v1/assets/{assetId}/archive}: see {@link AssetService#archive}. */
    @PostMapping("/v1/assets/{assetId}/archive")
    AssetResponse archive(Perimeter perimeter, @PathVariable UUID assetId, @Valid @RequestBody VersionOnly body) {
        return AssetResponse.of(assets.archive(perimeter, assetId, body.version()));
    }

    /** {@code POST /v1/assets/{assetId}/qr-code/rotate}: see {@link AssetService#rotateQr}. */
    @PostMapping("/v1/assets/{assetId}/qr-code/rotate")
    AssetResponse rotateQr(Perimeter perimeter, @PathVariable UUID assetId) {
        return AssetResponse.of(assets.rotateQr(perimeter, assetId));
    }

    /** {@code POST /v1/assets/{assetId}/documents}: see {@link AssetService#addDocument}. */
    @PostMapping("/v1/assets/{assetId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    AssetResponse addDocument(Perimeter perimeter, @PathVariable UUID assetId,
                              @Valid @RequestBody AssetDocumentInput body) {
        return AssetResponse.of(assets.addDocument(perimeter, assetId, body.fileId(), body.type(), body.title()));
    }

    /** {@code POST /v1/assets/{assetId}/documents/{documentId}/archive}: see {@link AssetService#archiveDocument}. */
    @PostMapping("/v1/assets/{assetId}/documents/{documentId}/archive")
    AssetResponse archiveDocument(Perimeter perimeter, @PathVariable UUID assetId, @PathVariable UUID documentId) {
        return AssetResponse.of(assets.archiveDocument(perimeter, assetId, documentId));
    }

    /** {@code POST /v1/assets/{assetId}/warranties}: see {@link AssetService#addWarranty}. */
    @PostMapping("/v1/assets/{assetId}/warranties")
    @ResponseStatus(HttpStatus.CREATED)
    AssetResponse addWarranty(Perimeter perimeter, @PathVariable UUID assetId, @Valid @RequestBody WarrantyInput body) {
        return AssetResponse.of(assets.addWarranty(perimeter, assetId, new WarrantyCommand(body.startDate(),
                body.endDate(), body.alertDaysBefore() == null ? 30 : body.alertDaysBefore(),
                Optional.ofNullable(body.documentId()), body.note())));
    }

    /** {@code PUT /v1/assets/{assetId}/warranties/{warrantyId}}: see {@link AssetService#updateWarranty}. */
    @PutMapping("/v1/assets/{assetId}/warranties/{warrantyId}")
    AssetResponse updateWarranty(Perimeter perimeter, @PathVariable UUID assetId, @PathVariable UUID warrantyId,
                                 @Valid @RequestBody UpdateWarrantyRequest body) {
        return AssetResponse.of(assets.updateWarranty(perimeter, assetId, warrantyId, new WarrantyCommand(
                body.startDate(), body.endDate(), body.alertDaysBefore() == null ? 30 : body.alertDaysBefore(),
                Optional.ofNullable(body.documentId()), body.note()), body.active(), body.version()));
    }

    /** {@code GET /v1/asset-qr/{code}}: see {@link AssetService#resolveQr}. */
    @GetMapping("/v1/asset-qr/{code}")
    AssetSummaryResponse resolveQr(Perimeter perimeter, @PathVariable String code) {
        return AssetSummaryResponse.of(assets.resolveQr(perimeter, code));
    }

    /** {@code POST /v1/sites/{siteId}/assets/import}: see {@link AssetImportService#importFile}. */
    @PostMapping(path = "/v1/sites/{siteId}/assets/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AssetImportReport importAssets(Perimeter perimeter, @PathVariable UUID siteId,
                                   @RequestParam(defaultValue = "false") boolean dryRun,
                                   @RequestParam("file") MultipartFile file) throws IOException {
        return AssetImportReport.of(imports.importFile(perimeter, new SiteId(siteId), file.getBytes(), dryRun));
    }

    /** {@code GET /v1/assets/import-template}: see {@link AssetImportService#template}. */
    @GetMapping("/v1/assets/import-template")
    ResponseEntity<byte[]> template(Perimeter perimeter, @RequestParam(defaultValue = "AR") Language language,
                                    @RequestParam(required = false) UUID siteId) {
        byte[] template = imports.template(perimeter, language, Optional.ofNullable(siteId).map(SiteId::new));
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"equipment-template.xlsx\"")
                .body(template);
    }
}
