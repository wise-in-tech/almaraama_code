package com.almaraama.assets.application;

import com.almaraama.assets.api.events.AssetCreatedV1;
import com.almaraama.assets.application.AssetService.AssetCommand;
import com.almaraama.assets.domain.Asset;
import com.almaraama.assets.domain.AssetFamily;
import com.almaraama.assets.domain.AssetModel;
import com.almaraama.assets.domain.AssetRepositories.Assets;
import com.almaraama.assets.domain.AssetRepositories.Families;
import com.almaraama.assets.domain.AssetRepositories.Suppliers;
import com.almaraama.assets.domain.AssetSheet;
import com.almaraama.assets.domain.AssetSheet.Line;
import com.almaraama.assets.domain.AssetState;
import com.almaraama.assets.domain.Supplier;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.BuildingSummary;
import com.almaraama.sites.api.OutdoorAreaSummary;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Excel import of equipment (G1, G2, ADR-027 revision 3): all or nothing, every error by row and column, the same
 * checks as {@link AssetService#create}. Each equipment gets its QR code.
 */
@Service
public class AssetImportService {

    private static final Logger LOG = LoggerFactory.getLogger(AssetImportService.class);
    private static final int MAX_ERRORS = 500;
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    /** Outcome of an import. */
    public enum Outcome {
        /** Dry run without error: nothing created. */
        CHECKED,
        /** Every row created. */
        IMPORTED,
        /** At least one error: nothing created. */
        REJECTED
    }

    /** One error of the report: Excel row, column key, code, shortened cell value. */
    public record ImportError(int row, String column, String code, String value) {
    }

    /** The report sent back. */
    public record Report(Outcome outcome, int totalRows, int createdCount, List<ImportError> errors) {
    }

    private final AssetSheet sheet;
    private final AssetService service;
    private final Assets assets;
    private final Families families;
    private final Suppliers suppliers;
    private final SitesApi sites;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param sheet     reader of the Excel file
     * @param service   the checks of an equipment
     * @param assets    the equipment table
     * @param families  families and models
     * @param suppliers supplier contacts
     * @param sites     buildings and outdoor areas
     * @param events    publication of {@code AssetCreatedV1}
     * @param clock     time of the import
     */
    AssetImportService(AssetSheet sheet, AssetService service, Assets assets, Families families, Suppliers suppliers,
                       SitesApi sites, ApplicationEventPublisher events, Clock clock) {
        this.sheet = sheet;
        this.service = service;
        this.assets = assets;
        this.families = families;
        this.suppliers = suppliers;
        this.sites = sites;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Reads, checks and (unless {@code dryRun} or an error) creates every equipment of the file in one transaction.
     * Needs {@code asset:create} on the site. {@code IMPORT_FILE_INVALID}, {@code IMPORT_FILE_TOO_LARGE}.
     */
    @Transactional
    public Report importFile(Perimeter perimeter, SiteId siteId, byte[] content, boolean dryRun) {
        SiteSummary site = sites.findSite(siteId).orElseThrow(Errors::notFound);
        perimeter.require(Permission.ASSET_CREATE, site.organizationId(), siteId);
        List<Line> lines = sheet.read(content);
        OrganizationId organization = site.organizationId();
        Map<String, AssetFamily> familyByCode = families.usable(Optional.of(organization), false).stream()
                .collect(Collectors.toMap(f -> f.code().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> b));
        Map<UUID, List<AssetModel>> modelsByFamily = families.modelsOf(familyByCode.values().stream()
                .map(AssetFamily::id).toList(), Optional.of(organization), false).stream()
                .collect(Collectors.groupingBy(AssetModel::familyId));
        Map<String, BuildingSummary> buildingByName = byName(sites.activeBuildings(siteId), BuildingSummary::name);
        Map<String, OutdoorAreaSummary> areaByName = byName(sites.activeOutdoorAreas(siteId),
                OutdoorAreaSummary::name);
        Map<String, Supplier> supplierByName = byName(suppliers.ofOrganization(organization, null, false),
                Supplier::name);

        List<ImportError> errors = new ArrayList<>();
        Map<String, Integer> givenReferences = new HashMap<>();
        Set<String> taken = new HashSet<>(assets.referencesOf(siteId));
        Map<Line, Asset> prepared = new LinkedHashMap<>();
        Instant now = clock.instant();
        for (Line line : lines) {
            int before = errors.size();
            Optional<AssetFamily> family = required(line, "name", errors).flatMap(n -> required(line, "family", errors))
                    .flatMap(code -> lookup(familyByCode, code.toUpperCase(Locale.ROOT), line, "family",
                            "IMPORT_FAMILY_UNKNOWN", errors));
            Optional<UUID> model = line.get("model").flatMap(name -> family.flatMap(f -> modelsByFamily
                    .getOrDefault(f.id(), List.of()).stream().filter(m -> m.name().equalsIgnoreCase(name)).findFirst()
                    .map(AssetModel::id).or(() -> {
                        error(errors, line, "model", "IMPORT_MODEL_UNKNOWN", name);
                        return Optional.empty();
                    })));
            Optional<UUID> building = line.get("building").flatMap(name -> lookup(buildingByName, key(name), line,
                    "building", "IMPORT_BUILDING_UNKNOWN", errors)).map(b -> b.id().value());
            Optional<UUID> area = line.get("outdoorArea").flatMap(name -> lookup(areaByName, key(name), line,
                    "outdoorArea", "IMPORT_OUTDOOR_AREA_UNKNOWN", errors)).map(OutdoorAreaSummary::id);
            Optional<UUID> supplier = line.get("supplier").flatMap(name -> lookup(supplierByName, key(name), line,
                    "supplier", "IMPORT_SUPPLIER_UNKNOWN", errors)).map(Supplier::id);
            Optional<Integer> floor = parse(line, "floor", errors, Integer::valueOf);
            Optional<AssetState> state = parse(line, "state", errors, v -> AssetState.valueOf(v.toUpperCase(Locale.ROOT)));
            Optional<LocalDate> commissioned = parse(line, "commissionedOn", errors, AssetImportService::date);
            line.get("reference").ifPresent(reference -> {
                Integer first = givenReferences.putIfAbsent(reference, line.excelRow());
                if (first != null) {
                    error(errors, line, "reference", "IMPORT_DUPLICATE_IN_FILE", reference);
                }
            });
            if (errors.size() != before || family.isEmpty()) {
                continue;
            }
            AssetCommand command = new AssetCommand(family.get().id(), model, line.get("name").orElse(null),
                    line.get("reference"), building, floor, area, state.orElse(AssetState.IN_SERVICE),
                    line.get("serialNumber").orElse(null), commissioned, supplier, Map.of());
            try {
                Asset.Details details = service.prepare(site, family.get().id(), command, Optional.empty(), taken);
                prepared.put(line, Asset.create(organization, siteId, family.get().id(), details, now));
            } catch (BusinessException e) {
                error(errors, line, columnOf(e.code()), e.code(), null);
            }
        }
        if (!errors.isEmpty() || dryRun) {
            Outcome outcome = errors.isEmpty() ? Outcome.CHECKED : Outcome.REJECTED;
            LOG.debug("Equipment import on site {}: {} rows, {} errors, {}", siteId.value(), lines.size(),
                    errors.size(), outcome);
            return new Report(outcome, lines.size(), 0, errors.stream().limit(MAX_ERRORS).toList());
        }
        for (Asset asset : prepared.values()) {
            assets.save(asset);
            events.publishEvent(new AssetCreatedV1(asset.id(), organization, siteId, asset.name(), perimeter.actor(),
                    now));
        }
        LOG.info("Equipment import on site {}: {} created", siteId.value(), prepared.size());
        return new Report(Outcome.IMPORTED, lines.size(), prepared.size(), List.of());
    }

    /**
     * The template in this language; with {@code siteId}, the site's buildings and outdoor areas and the organization's
     * suppliers are offered as lists. Needs {@code asset:create} on at least one site (or on the site given).
     */
    @Transactional(readOnly = true)
    public byte[] template(Perimeter perimeter, Language language, Optional<SiteId> siteId) {
        if (siteId.isEmpty()) {
            perimeter.requireAnywhere(Permission.ASSET_CREATE);
            return sheet.template(language, new AssetSheet.TemplateLists(families.usable(Optional.empty(), false)
                    .stream().map(AssetFamily::code).toList(), List.of(), List.of(), List.of()));
        }
        SiteSummary site = sites.findSite(siteId.get()).orElseThrow(Errors::notFound);
        perimeter.require(Permission.ASSET_CREATE, site.organizationId(), site.id());
        return sheet.template(language, new AssetSheet.TemplateLists(
                families.usable(Optional.of(site.organizationId()), false).stream().map(AssetFamily::code).toList(),
                sites.activeBuildings(site.id()).stream().map(BuildingSummary::name).toList(),
                sites.activeOutdoorAreas(site.id()).stream().map(OutdoorAreaSummary::name).toList(),
                suppliers.ofOrganization(site.organizationId(), null, false).stream().map(Supplier::name).toList()));
    }

    /** The column of the template an error code points to. */
    private static String columnOf(String code) {
        if (code.startsWith("ASSET_FAMILY")) {
            return "family";
        }
        if (code.startsWith("ASSET_MODEL")) {
            return "model";
        }
        if (code.startsWith("BUILDING")) {
            return "building";
        }
        if (code.startsWith("OUTDOOR_AREA")) {
            return "outdoorArea";
        }
        if (code.startsWith("SUPPLIER")) {
            return "supplier";
        }
        if (code.startsWith("ASSET_REFERENCE")) {
            return "reference";
        }
        return switch (code) {
            case "ASSET_NAME_INVALID" -> "name";
            case "ASSET_SERIAL_INVALID" -> "serialNumber";
            case "ASSET_LOCATION_INVALID" -> "floor";
            default -> null;
        };
    }

    /** A mandatory cell, else {@code IMPORT_VALUE_REQUIRED}. */
    private static Optional<String> required(Line line, String column, List<ImportError> errors) {
        Optional<String> value = line.get(column);
        if (value.isEmpty()) {
            error(errors, line, column, "IMPORT_VALUE_REQUIRED", null);
        }
        return value;
    }

    /** A named object, else {@code unknownCode}. */
    private static <T> Optional<T> lookup(Map<String, T> byKey, String key, Line line, String column,
                                          String unknownCode, List<ImportError> errors) {
        T found = byKey.get(key);
        if (found == null) {
            error(errors, line, column, unknownCode, line.get(column).orElse(key));
        }
        return Optional.ofNullable(found);
    }

    /** An optional cell parsed by {@code parser}, else {@code IMPORT_VALUE_INVALID}. */
    private static <T> Optional<T> parse(Line line, String column, List<ImportError> errors,
                                         Function<String, T> parser) {
        Optional<String> raw = line.get(column);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parser.apply(raw.get()));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            error(errors, line, column, "IMPORT_VALUE_INVALID", raw.get());
            return Optional.empty();
        }
    }

    /** A date typed {@code YYYY-MM-DD} or stored by Excel as a day number. */
    private static LocalDate date(String raw) {
        String value = raw.strip();
        if (value.matches("^\\d{1,6}(\\.0+)?$")) {
            return EXCEL_EPOCH.plusDays(Long.parseLong(value.replaceAll("\\..*", "")));
        }
        return LocalDate.parse(value);
    }

    /** Objects by their name, compared without case and surrounding spaces. */
    private static <T> Map<String, T> byName(List<T> list, Function<T, String> name) {
        return list.stream().collect(Collectors.toMap(t -> key(name.apply(t)), Function.identity(), (a, b) -> a));
    }

    /** The comparison key of a name. */
    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    /** Adds an error with the cell value shortened to 100 characters. */
    private static void error(List<ImportError> errors, Line line, String column, String code, String value) {
        String shown = value == null ? null : value.length() > 100 ? value.substring(0, 100) : value;
        errors.add(new ImportError(line.excelRow(), column, code, shown));
    }
}
