package com.almaraama.sites.adapters.in.web;

import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UnitImportError;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UnitImportReport;
import com.almaraama.sites.application.UnitImportService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Unit import from Excel and its template (A6, criterion 1, ADR-027). */
@RestController
class UnitImportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final UnitImportService imports;

    /** @param imports the import use case */
    UnitImportController(UnitImportService imports) {
        this.imports = imports;
    }

    /**
     * {@code POST /v1/sites/{id}/units/import}: needs {@code unit:import}; all or nothing, {@code dryRun} checks only.
     * The file is read in memory and never stored.
     */
    @PostMapping(value = "/v1/sites/{siteId}/units/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UnitImportReport importUnits(Perimeter perimeter, @PathVariable UUID siteId,
                                 @RequestPart("file") MultipartFile file,
                                 @RequestParam(defaultValue = "false") boolean dryRun) {
        UnitImportService.Report report = imports.importUnits(perimeter, new SiteId(siteId), bytes(file), dryRun);
        return new UnitImportReport(report.outcome().name(), report.totalRows(), report.createdCount(),
                report.errors().stream().map(e -> new UnitImportError(e.row(), e.column(), e.code(), e.value()))
                        .toList());
    }

    /** {@code GET /v1/units/import-template}: in the requested language (Arabic by default). */
    @GetMapping("/v1/units/import-template")
    ResponseEntity<byte[]> template(Perimeter perimeter, @RequestParam(required = false) Language language,
                                    @RequestParam(required = false) UUID siteId) {
        byte[] template = imports.template(perimeter, language == null ? Language.AR : language,
                Optional.ofNullable(siteId).map(SiteId::new));
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("units.xlsx")
                        .build().toString())
                .body(template);
    }

    /** The uploaded bytes; the file is never written anywhere (ADR-027). */
    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
