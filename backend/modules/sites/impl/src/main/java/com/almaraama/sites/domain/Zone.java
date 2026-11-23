package com.almaraama.sites.domain;

import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A node of the single zone tree (A8): a wilaya or a commune for Algeria, a region or a city elsewhere. Its
 * materialized {@code path} ({@code DZ/16/1601/}) lists the codes of its ancestors, so that filters and counts work at
 * every level by prefix. The application computes it for the zones it creates; a database trigger fills it for rows
 * loaded by hand. Never deleted: deactivated (M2).
 */
@Entity
@Table(schema = "sites", name = "zone")
public class Zone {

    @Id
    private UUID id;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "parent_code")
    private String parentCode;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "level", nullable = false)
    private int level;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_fr", nullable = false)
    private String nameFr;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ZoneStatus status;

    @Column(name = "created_by_organization_id")
    private UUID createdByOrganizationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** A8: the validated zone a provisional one was merged into (ADR-035). */
    @Column(name = "merged_into")
    private UUID mergedInto;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Zone() {
    }

    /**
     * A provisional zone created by a promoteur at the attachment level of its country (A8, criterion 3), under
     * {@code parent} when the upper level is loaded. Its code is generated ({@code P-} and 8 characters) and its path
     * computed from the parent. The caller checked the parent's country and level.
     */
    public static Zone provisional(Country country, Optional<Zone> parent, LocalizedName name,
                                   OrganizationId createdBy, Instant now) {
        Zone zone = new Zone();
        zone.id = UuidV7.next();
        zone.countryCode = country.code();
        zone.code = "P-" + zone.id.toString().replace("-", "").substring(24).toUpperCase(Locale.ROOT);
        zone.parentId = parent.map(p -> p.id).orElse(null);
        zone.parentCode = parent.map(p -> p.code).orElse(null);
        zone.level = country.attachmentLevel();
        zone.path = parent.map(p -> p.path).orElse(country.code() + "/") + zone.code + "/";
        zone.nameAr = name.ar();
        zone.nameEn = name.en();
        zone.nameFr = name.fr();
        zone.status = ZoneStatus.PROVISIONAL;
        zone.createdByOrganizationId = createdBy.value();
        zone.createdAt = now;
        return zone;
    }

    /**
     * A8, M7: a validated zone added by a platform administrator under {@code parent} (level 1 without parent). The
     * caller checked the parent's country and status. {@code ZONE_LEVEL_INVALID} beyond the country's levels, {@code
     * ZONE_CODE_INVALID}.
     */
    public static Zone validated(Country country, Optional<Zone> parent, String code, LocalizedName name,
                                 Instant now) {
        int level = parent.map(p -> p.level + 1).orElse(1);
        if (level > Math.max(country.levels().size(), country.attachmentLevel())) {
            throw rule("ZONE_LEVEL_INVALID", "Deeper than the levels of the country");
        }
        Zone zone = new Zone();
        zone.id = UuidV7.next();
        zone.countryCode = country.code();
        zone.code = checkCode(code);
        zone.level = level;
        zone.status = ZoneStatus.VALIDATED;
        zone.nameAr = name.ar();
        zone.nameEn = name.en();
        zone.nameFr = name.fr();
        zone.createdAt = now;
        zone.placeUnder(parent, now);
        return zone;
    }

    /** Corrects the code and the names; the path of the zone changes with its code (children: see the caller). */
    public void correct(String newCode, LocalizedName name, Instant now) {
        code = checkCode(newCode);
        nameAr = name.ar();
        nameEn = name.en();
        nameFr = name.fr();
        path = parentPathOf(path) + code + "/";
        updatedAt = now;
    }

    /**
     * A8, criterion 3: a provisional zone becomes validated, with its final code and, when given, its parent (the
     * caller checked the parent is a validated zone of the level just above). {@code ZONE_NOT_PROVISIONAL}.
     */
    public void validate(String finalCode, Optional<Zone> newParent, Optional<LocalizedName> names, Instant now) {
        requireProvisional();
        code = checkCode(finalCode);
        names.ifPresent(n -> {
            nameAr = n.ar();
            nameEn = n.en();
            nameFr = n.fr();
        });
        status = ZoneStatus.VALIDATED;
        if (newParent.isPresent()) {
            placeUnder(newParent, now);
        } else {
            path = parentPathOf(path) + code + "/";
            updatedAt = now;
        }
    }

    /**
     * A8: a provisional zone merged into a validated one: deactivated, kept with a pointer to the target (M2). The
     * caller moved its addresses. {@code ZONE_NOT_PROVISIONAL}, {@code ZONE_MERGE_TARGET_INVALID} (target not
     * validated, of another country or level).
     */
    public void mergeInto(Zone target, Instant now) {
        requireProvisional();
        if (target.status != ZoneStatus.VALIDATED || !target.countryCode.equals(countryCode) || target.level != level
                || target.id.equals(id)) {
            throw rule("ZONE_MERGE_TARGET_INVALID", "A validated zone of the same country and level");
        }
        status = ZoneStatus.DEACTIVATED;
        mergedInto = target.id;
        updatedAt = now;
    }

    /** A8, M2: no longer offered, kept with its addresses; {@code ZONE_ALREADY_DEACTIVATED}. */
    public void deactivate(Instant now) {
        if (status == ZoneStatus.DEACTIVATED) {
            throw rule("ZONE_ALREADY_DEACTIVATED", "Already deactivated");
        }
        status = ZoneStatus.DEACTIVATED;
        updatedAt = now;
    }

    /** Recomputes the path after the parent's path changed (a parent's code was corrected). */
    public void followParent(Zone parent, Instant now) {
        path = parent.path + code + "/";
        updatedAt = now;
    }

    /** Sets the parent, the parent's code and the materialized path. */
    private void placeUnder(Optional<Zone> parent, Instant now) {
        parentId = parent.map(p -> p.id).orElse(null);
        parentCode = parent.map(p -> p.code).orElse(null);
        path = parent.map(p -> p.path).orElse(countryCode + "/") + code + "/";
        updatedAt = now;
    }

    /** The path of the parent, from a zone's own path ({@code DZ/16/1601/} gives {@code DZ/16/}). */
    private static String parentPathOf(String path) {
        String withoutLast = path.substring(0, path.length() - 1);
        return withoutLast.substring(0, withoutLast.lastIndexOf('/') + 1);
    }

    /** {@code ZONE_NOT_PROVISIONAL} unless the zone waits for the administrators. */
    private void requireProvisional() {
        if (status != ZoneStatus.PROVISIONAL) {
            throw rule("ZONE_NOT_PROVISIONAL", "Only a provisional zone");
        }
    }

    /** A readable official code, 1 to 20 letters, digits, {@code _} or {@code -}; {@code ZONE_CODE_INVALID}. */
    private static String checkCode(String code) {
        String clean = code == null ? "" : code.strip();
        if (!clean.matches("^[A-Za-z0-9_-]{1,20}$")) {
            throw new BusinessException("ZONE_CODE_INVALID", ErrorCategory.VALIDATION, "1 to 20 letters or digits");
        }
        return clean;
    }

    /** A broken rule of the zone tree (422). */
    private static BusinessException rule(String code, String message) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, message);
    }

    /** The zone this provisional zone was merged into. */
    public Optional<UUID> mergedInto() {
        return Optional.ofNullable(mergedInto);
    }

    /** When it was created. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }

    /**
     * Whether this zone may be shown to people of these organizations: validated zones to everyone, provisional ones
     * only to the organization that created them (and the platform administrators), deactivated ones to nobody.
     */
    public boolean visibleTo(Set<OrganizationId> organizations, boolean platform) {
        return switch (status) {
            case VALIDATED -> true;
            case PROVISIONAL -> platform || createdByOrganizationId != null
                    && organizations.contains(new OrganizationId(createdByOrganizationId));
            case DEACTIVATED -> false;
        };
    }

    /** Identifier of the zone. */
    public UUID id() {
        return id;
    }

    /** ISO code of its country. */
    public String countryCode() {
        return countryCode;
    }

    /** Readable code, unique in the country ({@code 16}, {@code 1601}). */
    public String code() {
        return code;
    }

    /** The parent zone, absent at level 1 or for a provisional zone of a country that is not loaded. */
    public Optional<UUID> parentId() {
        return Optional.ofNullable(parentId);
    }

    /** Level in the tree, from 1. */
    public int level() {
        return level;
    }

    /** Materialized path, for filters by prefix. */
    public String path() {
        return path;
    }

    /** Name in the three languages. */
    public LocalizedName name() {
        return new LocalizedName(nameAr, nameEn, nameFr);
    }

    /** Validated, provisional or deactivated. */
    public ZoneStatus status() {
        return status;
    }

    /** The organization that created a provisional zone. */
    public Optional<OrganizationId> createdByOrganizationId() {
        return Optional.ofNullable(createdByOrganizationId).map(OrganizationId::new);
    }
}
