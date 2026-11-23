package com.almaraama.membership.adapters.in.web;

import com.almaraama.membership.adapters.in.web.MembershipWebDtos.Page;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PersonRef;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PlannedOccupancyResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PreRegistrationIssued;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PreRegistrationRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PreRegistrationResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.ReasonRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RenewPreRegistrationKeyRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.ResidentImportError;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.ResidentImportKey;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.ResidentImportReport;
import com.almaraama.membership.application.PeopleService;
import com.almaraama.membership.application.PreRegistrationService;
import com.almaraama.membership.application.ResidentImportService;
import com.almaraama.membership.domain.PlannedOccupancy;
import com.almaraama.membership.domain.PreRegistration;
import com.almaraama.membership.domain.PreRegistrationStatus;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SitesApi;
import com.almaraama.sites.api.UnitSummary;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Pre-registrations and the resident import (D3, criterion 6, ADR-027, ADR-034). Keys are returned only by the
 * creation, the renewal and the import, with {@code Cache-Control: no-store}; never by a read.
 */
@RestController
class PreRegistrationController {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PreRegistrationService preRegistrations;
    private final ResidentImportService imports;
    private final SitesApi sites;
    private final Names names;
    private final Clock clock;

    /** Wires the pre-registration use cases and the names shown in responses. */
    PreRegistrationController(PreRegistrationService preRegistrations, ResidentImportService imports, SitesApi sites,
                              Names names, Clock clock) {
        this.preRegistrations = preRegistrations;
        this.imports = imports;
        this.sites = sites;
        this.names = names;
        this.clock = clock;
    }

    /** {@code GET /v1/sites/{id}/pre-registrations}: needs {@code pre-registration:read}. */
    @GetMapping("/v1/sites/{siteId}/pre-registrations")
    Page<PreRegistrationResponse> list(Perimeter perimeter, @PathVariable UUID siteId,
                                       @RequestParam(required = false) PreRegistrationStatus status,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        PageResult<PreRegistration> found = preRegistrations.list(perimeter, new SiteId(siteId), status,
                PeopleController.shorten(q), PageQuery.of(page, size));
        Instant now = clock.instant();
        Map<UnitId, UnitSummary> units = units(found.items());
        Map<UUID, PersonRef> creators = names.people(found.items().stream().map(p -> p.createdBy().value())
                .collect(Collectors.toSet()));
        return Page.of(found, p -> response(p, units, creators, now));
    }

    /**
     * {@code POST /v1/sites/{id}/pre-registrations}: needs {@code pre-registration:create} and {@code
     * occupancy:create}; the key is in this answer only.
     */
    @PostMapping("/v1/sites/{siteId}/pre-registrations")
    ResponseEntity<PreRegistrationIssued> create(Perimeter perimeter, @PathVariable UUID siteId,
                                                 @Valid @RequestBody PreRegistrationRequest request) {
        PreRegistrationService.Draft draft = new PreRegistrationService.Draft(
                new PeopleService.PersonInput(request.person().firstName(), request.person().lastName(),
                        request.person().email(), request.person().phone(), request.person().language()),
                request.plannedOccupancies().stream().map(p -> new PlannedOccupancy(new UnitId(p.unitId()), p.role(),
                        Boolean.TRUE.equals(p.primaryTenant()))).toList());
        PreRegistration.Issued issued = preRegistrations.create(perimeter, new SiteId(siteId), draft,
                days(request.keyValidDays()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(issued(issued));
    }

    /** {@code GET /v1/pre-registrations/{id}}: needs {@code pre-registration:read}; never the key. */
    @GetMapping("/v1/pre-registrations/{preRegistrationId}")
    PreRegistrationResponse get(Perimeter perimeter, @PathVariable UUID preRegistrationId) {
        return response(preRegistrations.get(perimeter, preRegistrationId));
    }

    /** {@code POST /v1/pre-registrations/{id}/renew-key}: a new key, in this answer only. */
    @PostMapping("/v1/pre-registrations/{preRegistrationId}/renew-key")
    ResponseEntity<PreRegistrationIssued> renewKey(Perimeter perimeter, @PathVariable UUID preRegistrationId,
                                                   @Valid @RequestBody RenewPreRegistrationKeyRequest request) {
        PreRegistration.Issued issued = preRegistrations.renewKey(perimeter, preRegistrationId,
                days(request.keyValidDays()), request.version());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(issued(issued));
    }

    /** {@code POST /v1/pre-registrations/{id}/cancel}: needs {@code pre-registration:cancel}. */
    @PostMapping("/v1/pre-registrations/{preRegistrationId}/cancel")
    PreRegistrationResponse cancel(Perimeter perimeter, @PathVariable UUID preRegistrationId,
                                   @Valid @RequestBody ReasonRequest request) {
        return response(preRegistrations.cancel(perimeter, preRegistrationId, request.reason()));
    }

    /**
     * {@code POST /v1/sites/{id}/residents/import}: an {@code .xlsx} file of planned residents (ADR-027); the keys are
     * in this answer only.
     */
    @PostMapping(value = "/v1/sites/{siteId}/residents/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResidentImportReport> importResidents(Perimeter perimeter, @PathVariable UUID siteId,
                                                         @RequestPart("file") MultipartFile file,
                                                         @RequestParam(defaultValue = "false") boolean dryRun,
                                                         @RequestParam(required = false) Integer keyValidDays) {
        ResidentImportService.Report report = imports.importResidents(perimeter, new SiteId(siteId), bytes(file),
                dryRun, days(keyValidDays));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ResidentImportReport(
                report.outcome().name(), report.totalRows(), report.createdCount(),
                report.errors().stream().map(e -> new ResidentImportError(e.row(), e.column(), e.code(), e.value()))
                        .toList(),
                report.keys().stream().map(k -> new ResidentImportKey(k.preRegistration().id(),
                        k.preRegistration().firstName(), k.preRegistration().lastName(), k.preRegistration().email(),
                        k.key(), k.preRegistration().expiresAt())).toList()));
    }

    /** {@code GET /v1/residents/import-template}: the template in the requested language (Arabic by default). */
    @GetMapping("/v1/residents/import-template")
    ResponseEntity<byte[]> template(Perimeter perimeter, @RequestParam(required = false) Language language) {
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("residents.xlsx").build().toString())
                .body(imports.template(perimeter, language == null ? Language.AR : language));
    }

    // ------------------------------------------------------------------------------------------- mapping

    /** The key validity in days, 30 by default (D3). */
    private static int days(Integer keyValidDays) {
        return keyValidDays == null ? PreRegistration.DEFAULT_VALID_DAYS : keyValidDays;
    }

    /** The uploaded bytes; the file is never written anywhere (ADR-027). */
    static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A pre-registration with its key. */
    private PreRegistrationIssued issued(PreRegistration.Issued issued) {
        return new PreRegistrationIssued(response(issued.preRegistration()), issued.key());
    }

    /** One pre-registration with its unit references and creator's name. */
    private PreRegistrationResponse response(PreRegistration pre) {
        return response(pre, units(List.of(pre)), names.people(List.of(pre.createdBy().value())), clock.instant());
    }

    /** The units planned in these pre-registrations, in one query. */
    private Map<UnitId, UnitSummary> units(Collection<PreRegistration> list) {
        return sites.findUnits(list.stream().flatMap(p -> p.plannedOccupancies().stream())
                        .map(PlannedOccupancy::unitId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(UnitSummary::id, Function.identity()));
    }

    /** Response body of a pre-registration, never its key. */
    private static PreRegistrationResponse response(PreRegistration p, Map<UnitId, UnitSummary> units,
                                                    Map<UUID, PersonRef> creators, Instant now) {
        return new PreRegistrationResponse(p.id(), p.siteId().value(), p.firstName(), p.lastName(), p.email(),
                p.phone(), p.language(),
                p.plannedOccupancies().stream().filter(o -> units.containsKey(o.unitId()))
                        .map(o -> new PlannedOccupancyResponse(o.unitId().value(), o.role(), o.primaryTenant(),
                                units.get(o.unitId()).reference(), units.get(o.unitId()).type())).toList(),
                p.status(now), p.origin(), p.expiresAt(), creators.get(p.createdBy().value()), p.createdAt(),
                p.usedAt().orElse(null), p.membershipId().orElse(null), p.cancelledAt().orElse(null),
                p.cancelReason().orElse(null), p.version());
    }
}
