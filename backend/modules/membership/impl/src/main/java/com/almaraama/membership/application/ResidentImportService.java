package com.almaraama.membership.application;

import com.almaraama.membership.domain.OccupancyRole;
import com.almaraama.membership.domain.PlannedOccupancy;
import com.almaraama.membership.domain.PreRegistration;
import com.almaraama.membership.domain.PreRegistrationOrigin;
import com.almaraama.membership.domain.ResidentSheet;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import com.almaraama.sites.api.UnitSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resident import (D3, ADR-027): one Excel row per planned unit; the rows of one e-mail make one pre-registration.
 * All or nothing: with {@code dryRun} or any error nothing is created and the report lists every error by row and
 * column. Otherwise every pre-registration is created in one transaction and the keys are returned this once.
 * Neither cell values nor keys are logged.
 */
@Service
public class ResidentImportService {

    private static final Logger LOG = LoggerFactory.getLogger(ResidentImportService.class);
    static final int MAX_ERRORS = 500;
    private static final int MAX_NAME = 100;
    private static final int MAX_ERROR_VALUE = 100;
    private static final Set<String> YES = Set.of("TRUE", "YES", "OUI", "1", "نعم");
    private static final Set<String> NO = Set.of("FALSE", "NO", "NON", "0", "لا");

    /** What the file gave. {@code keys} is filled only when {@code outcome} is {@code IMPORTED}. */
    public record Report(Outcome outcome, int totalRows, int createdCount, List<RowError> errors,
                         List<PreRegistration.Issued> keys) {
    }

    /** {@code CHECKED}: dry run without error; {@code IMPORTED}: created; {@code REJECTED}: nothing created. */
    public enum Outcome {
        CHECKED, IMPORTED, REJECTED
    }

    /** One error: Excel row, column key (or {@code null}), error code, the cell value shortened (never a key). */
    public record RowError(int row, String column, String code, String value) {
    }

    /** The rows of one e-mail, in file order. */
    private record Person(String emailKey, List<ResidentSheet.Line> lines) {
    }

    private final ResidentSheet sheet;
    private final PreRegistrationService preRegistrations;
    private final SitesApi sites;
    private final Clock clock;

    /** Wires the file reader and the pre-registration rules. */
    ResidentImportService(ResidentSheet sheet, PreRegistrationService preRegistrations, SitesApi sites, Clock clock) {
        this.sheet = sheet;
        this.preRegistrations = preRegistrations;
        this.sites = sites;
        this.clock = clock;
    }

    /**
     * Imports the file; needs {@code pre-registration:create} and {@code occupancy:create} on the site. File errors:
     * {@code IMPORT_FILE_INVALID}, {@code IMPORT_FILE_TOO_LARGE}; row errors go into the report.
     */
    @Transactional
    public Report importResidents(Perimeter perimeter, SiteId siteId, byte[] file, boolean dryRun, int keyValidDays) {
        SiteSummary site = preRegistrations.requireCreate(perimeter, siteId);
        List<ResidentSheet.Line> lines = sheet.read(file);
        Map<String, UnitSummary> units = sites.unitsOfSite(siteId).stream().filter(UnitSummary::active)
                .collect(Collectors.toMap(u -> u.reference().toLowerCase(Locale.ROOT), Function.identity(),
                        (a, b) -> a));
        Instant now = clock.instant();
        PersonId by = RoleAdministration.actor(perimeter);
        List<RowError> errors = new ArrayList<>();
        List<PreRegistration.Issued> checked = new ArrayList<>();
        PreRegistrationService.Batch batch = new PreRegistrationService.Batch();

        for (Person person : group(lines, errors)) {
            Optional<PreRegistrationService.Draft> draft = draft(person, units, errors);
            if (draft.isEmpty()) {
                continue;
            }
            try {
                checked.add(preRegistrations.check(site, draft.get(), keyValidDays, PreRegistrationOrigin.IMPORT, by,
                        now, batch));
            } catch (BusinessException e) {
                ResidentSheet.Line first = person.lines().getFirst();
                errors.add(error(first, columnOf(e.code()), e.code(), valueOf(first, columnOf(e.code()))));
            }
        }

        int total = lines.size();
        if (!errors.isEmpty()) {
            LOG.debug("Resident import on site {} rejected: {} rows, {} errors", siteId.value(), total, errors.size());
            return new Report(Outcome.REJECTED, total, 0, limit(errors), List.of());
        }
        if (dryRun) {
            return new Report(Outcome.CHECKED, total, 0, List.of(), List.of());
        }
        checked.forEach(issued -> preRegistrations.save(perimeter, issued, now));
        LOG.info("Resident import on site {}: {} pre-registrations from {} rows", siteId.value(), checked.size(),
                total);
        return new Report(Outcome.IMPORTED, total, checked.size(), List.of(), checked);
    }

    /** The template in the person's language; needs {@code pre-registration:create} on some site. */
    public byte[] template(Perimeter perimeter, Language language) {
        perimeter.requireAnywhere(Permission.PRE_REGISTRATION_CREATE);
        return sheet.template(language);
    }

    /**
     * Groups the lines by e-mail (lower case), in file order; a line without e-mail is an error ({@code
     * IMPORT_VALUE_REQUIRED}).
     */
    private static List<Person> group(List<ResidentSheet.Line> lines, List<RowError> errors) {
        Map<String, List<ResidentSheet.Line>> byEmail = new LinkedHashMap<>();
        for (ResidentSheet.Line line : lines) {
            Optional<String> email = line.get("email");
            if (email.isEmpty()) {
                errors.add(error(line, "email", "IMPORT_VALUE_REQUIRED", null));
                continue;
            }
            byEmail.computeIfAbsent(email.get().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(line);
        }
        return byEmail.entrySet().stream().map(e -> new Person(e.getKey(), e.getValue())).toList();
    }

    /**
     * Reads the person and the planned units of one e-mail's lines; records every row error and returns empty when
     * there is one: required values, closed values ({@code IMPORT_VALUE_INVALID}), unknown unit ({@code
     * IMPORT_UNIT_UNKNOWN}), same unit twice ({@code IMPORT_DUPLICATE_IN_FILE}), different person details on two rows
     * ({@code IMPORT_PERSON_MISMATCH}).
     */
    private static Optional<PreRegistrationService.Draft> draft(Person person, Map<String, UnitSummary> units,
                                                                List<RowError> errors) {
        int before = errors.size();
        ResidentSheet.Line first = person.lines().getFirst();
        String lastName = name(first, "lastName", errors);
        String firstName = name(first, "firstName", errors);
        String phone = required(first, "phone", errors);
        Language language = required(first, "language", errors) == null ? null : language(first, errors);
        List<PlannedOccupancy> planned = new ArrayList<>();
        Set<String> seenUnits = new HashSet<>();
        for (ResidentSheet.Line line : person.lines()) {
            if (line != first && !samePerson(first, line)) {
                errors.add(error(line, "email", "IMPORT_PERSON_MISMATCH", line.get("email").orElse(null)));
                continue;
            }
            String reference = required(line, "unit", errors);
            String role = required(line, "role", errors);
            if (reference == null || role == null) {
                continue;
            }
            UnitSummary unit = units.get(reference.toLowerCase(Locale.ROOT));
            if (unit == null) {
                errors.add(error(line, "unit", "IMPORT_UNIT_UNKNOWN", reference));
                continue;
            }
            if (!seenUnits.add(reference.toLowerCase(Locale.ROOT))) {
                errors.add(error(line, "unit", "IMPORT_DUPLICATE_IN_FILE", reference));
                continue;
            }
            Optional<OccupancyRole> occupancyRole = role(role);
            Optional<Boolean> primary = yesNo(line.get("primaryTenant").orElse("FALSE"));
            if (occupancyRole.isEmpty()) {
                errors.add(error(line, "role", "IMPORT_VALUE_INVALID", role));
                continue;
            }
            if (primary.isEmpty()) {
                errors.add(error(line, "primaryTenant", "IMPORT_VALUE_INVALID", line.get("primaryTenant").orElse("")));
                continue;
            }
            try {
                planned.add(new PlannedOccupancy(unit.id(), occupancyRole.get(), primary.get()));
            } catch (BusinessException e) {
                errors.add(error(line, "primaryTenant", e.code(), line.get("primaryTenant").orElse("")));
            }
        }
        if (errors.size() > before) {
            return Optional.empty();
        }
        return Optional.of(new PreRegistrationService.Draft(new PeopleService.PersonInput(firstName, lastName,
                first.get("email").orElseThrow(), phone, language), planned));
    }

    /** Whether two lines of one e-mail give the same name, phone and language (case and spaces ignored). */
    private static boolean samePerson(ResidentSheet.Line a, ResidentSheet.Line b) {
        for (String key : List.of("lastName", "firstName", "phone", "language")) {
            String x = a.get(key).orElse("").toLowerCase(Locale.ROOT).replaceAll("\\s", "");
            String y = b.get(key).orElse("").toLowerCase(Locale.ROOT).replaceAll("\\s", "");
            if (!x.equals(y)) {
                return false;
            }
        }
        return true;
    }

    /** A first or last name, 1 to 100 characters. */
    private static String name(ResidentSheet.Line line, String key, List<RowError> errors) {
        String value = required(line, key, errors);
        if (value != null && value.length() > MAX_NAME) {
            errors.add(error(line, key, "IMPORT_VALUE_INVALID", value));
            return null;
        }
        return value;
    }

    /** The cell, or {@code IMPORT_VALUE_REQUIRED}. */
    private static String required(ResidentSheet.Line line, String key, List<RowError> errors) {
        Optional<String> value = line.get(key);
        if (value.isEmpty()) {
            errors.add(error(line, key, "IMPORT_VALUE_REQUIRED", null));
            return null;
        }
        return value.get();
    }

    /** {@code AR}, {@code EN} or {@code FR}, any case; {@code IMPORT_VALUE_INVALID} otherwise. */
    private static Language language(ResidentSheet.Line line, List<RowError> errors) {
        String value = line.get("language").orElse("");
        try {
            return Language.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(error(line, "language", "IMPORT_VALUE_INVALID", value));
            return null;
        }
    }

    /** {@code OWNER} or {@code TENANT}, any case. */
    private static Optional<OccupancyRole> role(String value) {
        try {
            return Optional.of(OccupancyRole.valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** A yes/no cell in the template's codes or common words of the three languages. */
    static Optional<Boolean> yesNo(String value) {
        String clean = value.strip().toUpperCase(Locale.ROOT);
        if (YES.contains(clean)) {
            return Optional.of(true);
        }
        return NO.contains(clean) ? Optional.of(false) : Optional.empty();
    }

    /** The template column most related to an error code of the pre-registration rules. */
    private static String columnOf(String code) {
        return switch (code) {
            case "EMAIL_INVALID", "MEMBERSHIP_EXISTS", "PRE_REGISTRATION_EXISTS" -> "email";
            case "PHONE_INVALID" -> "phone";
            case "PRE_REGISTRATION_NAME_INVALID" -> "lastName";
            case "OCCUPANCY_OWNER_EXISTS", "UNIT_NOT_IN_SITE", "PRE_REGISTRATION_UNITS_INVALID" -> "unit";
            case "OCCUPANCY_PRIMARY_TENANT_EXISTS", "OCCUPANCY_PRIMARY_TENANT_NOT_TENANT" -> "primaryTenant";
            default -> null;
        };
    }

    /** The cell of a column, for the report. */
    private static String valueOf(ResidentSheet.Line line, String column) {
        return column == null ? null : line.get(column).orElse(null);
    }

    /** An error of a row, with the value shortened to 100 characters. */
    private static RowError error(ResidentSheet.Line line, String column, String code, String value) {
        String shortened = value == null ? null
                : value.length() > MAX_ERROR_VALUE ? value.substring(0, MAX_ERROR_VALUE) : value;
        return new RowError(line.excelRow(), column, code, shortened);
    }

    /** At most 500 errors in the report. */
    private static List<RowError> limit(List<RowError> errors) {
        return errors.size() > MAX_ERRORS ? List.copyOf(errors.subList(0, MAX_ERRORS)) : List.copyOf(errors);
    }
}
