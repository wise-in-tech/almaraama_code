package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.UnitType;
import com.almaraama.sites.api.events.UnitCreatedV1;
import com.almaraama.sites.domain.Building;
import com.almaraama.sites.domain.BuildingRepository;
import com.almaraama.sites.domain.OutdoorArea;
import com.almaraama.sites.domain.OutdoorAreaRepository;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.Unit;
import com.almaraama.sites.domain.UnitCategory;
import com.almaraama.sites.domain.UnitCategoryRepository;
import com.almaraama.sites.domain.UnitRepository;
import com.almaraama.sites.domain.UnitSheet;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit import from Excel (A6, A7, criterion 1, ADR-027): one row per unit with its mandatory category. All or nothing:
 * with {@code dryRun} or any error nothing is created, and the report lists every error by Excel row and column.
 * Otherwise every unit is created in one transaction (assumed exception to CF-4, ADR-027) with one {@code
 * UnitCreatedV1} each. Buildings and outdoor areas are matched by name, categories by code, annexes by reference
 * (in the site or earlier in the file). Cell values are never logged.
 */
@Service
public class UnitImportService {

    private static final Logger LOG = LoggerFactory.getLogger(UnitImportService.class);
    static final int MAX_ERRORS = 500;
    private static final int MAX_ERROR_VALUE = 100;

    /** What the file gave. */
    public record Report(Outcome outcome, int totalRows, int createdCount, List<RowError> errors) {
    }

    /** {@code CHECKED}: dry run without error; {@code IMPORTED}: created; {@code REJECTED}: nothing created. */
    public enum Outcome {
        CHECKED, IMPORTED, REJECTED
    }

    /** One error: Excel row, column key (or {@code null}), error code, the cell value shortened. */
    public record RowError(int row, String column, String code, String value) {
    }

    /** What the site already holds, loaded once for the whole file. */
    private record Context(Site site, Map<String, Building> buildings, Map<String, OutdoorArea> areas,
                           Map<String, List<UnitCategory>> categories, Map<String, Unit> existing) {
    }

    private final UnitSheet sheet;
    private final UnitRepository units;
    private final BuildingRepository buildings;
    private final OutdoorAreaRepository areas;
    private final UnitCategoryRepository categories;
    private final SiteService sites;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the file reader and the ports of the units. */
    UnitImportService(UnitSheet sheet, UnitRepository units, BuildingRepository buildings, OutdoorAreaRepository areas,
                      UnitCategoryRepository categories, SiteService sites, ApplicationEventPublisher events,
                      Clock clock) {
        this.sheet = sheet;
        this.units = units;
        this.buildings = buildings;
        this.areas = areas;
        this.categories = categories;
        this.sites = sites;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Imports the file; needs {@code unit:import} on the site. File errors: {@code IMPORT_FILE_INVALID}, {@code
     * IMPORT_FILE_TOO_LARGE}; row errors go into the report: {@code IMPORT_VALUE_REQUIRED}, {@code
     * IMPORT_VALUE_INVALID}, {@code IMPORT_DUPLICATE_IN_FILE}, {@code UNIT_REFERENCE_TAKEN}, {@code
     * IMPORT_BUILDING_UNKNOWN}, {@code IMPORT_OUTDOOR_AREA_UNKNOWN}, {@code IMPORT_ANNEX_UNKNOWN}, {@code
     * IMPORT_CATEGORY_UNKNOWN}, and the unit rules ({@code UNIT_LOCATION_INVALID}, {@code UNIT_FLOOR_INVALID}, {@code
     * UNIT_ANNEX_INVALID}, {@code UNIT_CATEGORY_TYPE_MISMATCH}…).
     */
    @Transactional
    public Report importUnits(Perimeter perimeter, SiteId siteId, byte[] file, boolean dryRun) {
        Site site = sites.visible(perimeter, siteId);
        perimeter.require(Permission.UNIT_IMPORT, site.organizationId(), siteId);
        List<UnitSheet.Line> lines = sheet.read(file);
        long started = System.nanoTime();
        Context context = context(site);
        Instant now = clock.instant();
        List<RowError> errors = new ArrayList<>();
        Map<String, Unit> created = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        // Annexes last, so that an annex may point to an apartment of the same file (A7).
        List<UnitSheet.Line> ordered = new ArrayList<>(lines.stream().filter(l -> l.get("annexOf").isEmpty()).toList());
        ordered.addAll(lines.stream().filter(l -> l.get("annexOf").isPresent()).toList());
        for (UnitSheet.Line line : ordered) {
            row(context, line, created, seen, errors, now).ifPresent(u -> created.put(key(u.reference()), u));
        }
        errors.sort((a, b) -> Integer.compare(a.row(), b.row()));

        int total = lines.size();
        if (!errors.isEmpty()) {
            LOG.debug("Unit import on site {} rejected: {} rows, {} errors", siteId.value(), total, errors.size());
            return new Report(Outcome.REJECTED, total, 0, limit(errors));
        }
        if (dryRun) {
            return new Report(Outcome.CHECKED, total, 0, List.of());
        }
        // In file order with annexes last: an annex row needs its main unit inserted first (foreign key, A7).
        List<Unit> saved = units.saveAll(List.copyOf(created.values()));
        for (Unit unit : saved) {
            events.publishEvent(new UnitCreatedV1(unit.id(), siteId, site.organizationId(), unit.buildingId(),
                    unit.type(), unit.reference(), perimeter.actor(), now));
        }
        LOG.info("Unit import on site {}: {} units created in {} ms", siteId.value(), saved.size(),
                (System.nanoTime() - started) / 1_000_000);
        return new Report(Outcome.IMPORTED, total, saved.size(), List.of());
    }

    /**
     * The template in the person's language; with a site, its usable category codes are offered (needs {@code
     * unit:import} on it), otherwise the platform defaults (needs {@code unit:import} somewhere).
     */
    @Transactional(readOnly = true)
    public byte[] template(Perimeter perimeter, Language language, Optional<SiteId> siteId) {
        List<UnitCategory> offered;
        if (siteId.isPresent()) {
            Site site = sites.visible(perimeter, siteId.get());
            perimeter.require(Permission.UNIT_IMPORT, site.organizationId(), site.id());
            offered = categories.usable(site.organizationId(), Optional.of(site.id()), null, false);
        } else {
            perimeter.requireAnywhere(Permission.UNIT_IMPORT);
            offered = categories.platformDefaults(null, false);
        }
        return sheet.template(language, offered.stream().map(UnitCategory::code).distinct().toList());
    }

    /** Buildings, outdoor areas, categories and existing units of the site, by their lower-case name or code. */
    private Context context(Site site) {
        Map<String, Building> byName = buildings.ofSite(site.id(), false).stream()
                .collect(Collectors.toMap(b -> key(b.name()), Function.identity(), (a, b) -> a));
        Map<String, OutdoorArea> areaByName = areas.ofSite(site.id(), false).stream()
                .collect(Collectors.toMap(a -> key(a.name()), Function.identity(), (a, b) -> a));
        Map<String, List<UnitCategory>> byCode = categories.usable(site.organizationId(), Optional.of(site.id()), null,
                false).stream().collect(Collectors.groupingBy(c -> key(c.code())));
        Map<String, Unit> existing = units.ofSite(site.id()).stream()
                .collect(Collectors.toMap(u -> key(u.reference()), Function.identity(), (a, b) -> a));
        return new Context(site, byName, areaByName, byCode, existing);
    }

    /** Checks one row and builds its unit in memory; records the row's errors and returns empty when there is one. */
    private Optional<Unit> row(Context context, UnitSheet.Line line, Map<String, Unit> created, Set<String> seen,
                               List<RowError> errors, Instant now) {
        int before = errors.size();
        String reference = required(line, "reference", errors);
        if (reference != null) {
            if (!seen.add(key(reference))) {
                errors.add(error(line, "reference", "IMPORT_DUPLICATE_IN_FILE", reference));
            } else if (context.existing().containsKey(key(reference))) {
                errors.add(error(line, "reference", "UNIT_REFERENCE_TAKEN", reference));
            }
        }
        UnitType type = enumValue(line, "type", UnitType.class, errors);
        UnitCategory category = category(context, line, type, errors);
        Optional<Building> building = line.get("building").map(name -> {
            Building found = context.buildings().get(key(name));
            if (found == null) {
                errors.add(error(line, "building", "IMPORT_BUILDING_UNKNOWN", name));
            }
            return found;
        });
        Optional<OutdoorArea> area = line.get("outdoorArea").map(name -> {
            OutdoorArea found = context.areas().get(key(name));
            if (found == null) {
                errors.add(error(line, "outdoorArea", "IMPORT_OUTDOOR_AREA_UNKNOWN", name));
            }
            return found;
        });
        Integer floor = integer(line, "floor", errors);
        Optional<Unit> annexOf = line.get("annexOf").map(ref -> {
            Unit main = created.getOrDefault(key(ref), context.existing().get(key(ref)));
            if (main == null) {
                errors.add(error(line, "annexOf", "IMPORT_ANNEX_UNKNOWN", ref));
            }
            return main;
        });
        boolean saleable = yesNo(line, "saleable", errors);
        boolean rentable = yesNo(line, "rentable", errors);
        if (errors.size() > before) {
            return Optional.empty();
        }
        Unit.Spec spec = new Unit.Spec(type, category.id(), reference, building.map(b -> b.id().value()).orElse(null),
                area.map(OutdoorArea::id).orElse(null), floor, annexOf.map(u -> u.id().value()).orElse(null),
                saleable, rentable);
        try {
            return Optional.of(Unit.create(context.site(), spec, new Unit.Placement(building, area, annexOf,
                    category), now));
        } catch (BusinessException e) {
            String column = switch (e.code()) {
                case "UNIT_REFERENCE_INVALID" -> "reference";
                case "UNIT_FLOOR_INVALID" -> "floor";
                case "UNIT_ANNEX_INVALID" -> "annexOf";
                case "UNIT_CATEGORY_TYPE_MISMATCH", "UNIT_CATEGORY_ARCHIVED" -> "category";
                case "UNIT_LOCATION_INVALID" -> "building";
                default -> null;
            };
            errors.add(error(line, column, e.code(), column == null ? null : line.get(column).orElse(null)));
            return Optional.empty();
        }
    }

    /**
     * The category of the row by code (A6): one of the type when several types share the code; {@code
     * IMPORT_CATEGORY_UNKNOWN} when no usable category has it, {@code UNIT_CATEGORY_TYPE_MISMATCH} when only another
     * type has it.
     */
    private static UnitCategory category(Context context, UnitSheet.Line line, UnitType type, List<RowError> errors) {
        String code = required(line, "category", errors);
        if (code == null) {
            return null;
        }
        List<UnitCategory> matching = context.categories().getOrDefault(key(code), List.of());
        if (matching.isEmpty()) {
            errors.add(error(line, "category", "IMPORT_CATEGORY_UNKNOWN", code));
            return null;
        }
        if (type == null) {
            return null;
        }
        return matching.stream().filter(c -> c.unitType() == type).findFirst().orElseGet(() -> {
            errors.add(error(line, "category", "UNIT_CATEGORY_TYPE_MISMATCH", code));
            return null;
        });
    }

    /** The cell, or {@code IMPORT_VALUE_REQUIRED}. */
    private static String required(UnitSheet.Line line, String key, List<RowError> errors) {
        Optional<String> value = line.get(key);
        if (value.isEmpty()) {
            errors.add(error(line, key, "IMPORT_VALUE_REQUIRED", null));
            return null;
        }
        return value.get();
    }

    /** A mandatory closed value of an enumeration, any case; {@code IMPORT_VALUE_INVALID} otherwise. */
    private static <E extends Enum<E>> E enumValue(UnitSheet.Line line, String key, Class<E> type,
                                                   List<RowError> errors) {
        String value = required(line, key, errors);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(error(line, key, "IMPORT_VALUE_INVALID", value));
            return null;
        }
    }

    /** An optional whole number (Excel may write {@code 2.0}); {@code IMPORT_VALUE_INVALID} otherwise. */
    private static Integer integer(UnitSheet.Line line, String key, List<RowError> errors) {
        Optional<String> value = line.get(key);
        if (value.isEmpty()) {
            return null;
        }
        String clean = value.get().strip().replaceAll("\\.0+$", "");
        try {
            return Integer.valueOf(clean);
        } catch (NumberFormatException e) {
            errors.add(error(line, key, "IMPORT_VALUE_INVALID", value.get()));
            return null;
        }
    }

    /** An optional yes/no cell, {@code true} when empty; {@code IMPORT_VALUE_INVALID} for anything else. */
    private static boolean yesNo(UnitSheet.Line line, String key, List<RowError> errors) {
        Optional<String> value = line.get(key);
        if (value.isEmpty()) {
            return true;
        }
        return switch (value.get().strip().toUpperCase(Locale.ROOT)) {
            case "TRUE", "YES", "OUI", "1", "نعم" -> true;
            case "FALSE", "NO", "NON", "0", "لا" -> false;
            default -> {
                errors.add(error(line, key, "IMPORT_VALUE_INVALID", value.get()));
                yield true;
            }
        };
    }

    /** The matching key of a name, code or reference: trimmed, lower case. */
    private static String key(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    /** An error of a row, with the value shortened to 100 characters. */
    private static RowError error(UnitSheet.Line line, String column, String code, String value) {
        String shortened = value == null ? null
                : value.length() > MAX_ERROR_VALUE ? value.substring(0, MAX_ERROR_VALUE) : value;
        return new RowError(line.excelRow(), column, code, shortened);
    }

    /** At most 500 errors in the report. */
    private static List<RowError> limit(List<RowError> errors) {
        return errors.size() > MAX_ERRORS ? List.copyOf(errors.subList(0, MAX_ERRORS)) : List.copyOf(errors);
    }
}
