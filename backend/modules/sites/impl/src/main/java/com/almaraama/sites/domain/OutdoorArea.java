package com.almaraama.sites.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** An outdoor area of a site: parking, storage, playground, stadium (A3). Never deleted (M2). */
@Entity
@Table(schema = "sites", name = "outdoor_area")
public class OutdoorArea {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OutdoorAreaType type;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected OutdoorArea() {
    }

    /** A new active outdoor area of the site. */
    public static OutdoorArea create(Site site, OutdoorAreaType type, String name, Instant now) {
        OutdoorArea area = new OutdoorArea();
        area.id = UuidV7.next();
        area.organizationId = site.organizationId().value();
        area.siteId = site.id().value();
        area.createdAt = now;
        area.update(type, name, true, now);
        return area;
    }

    /** Changes type, name and state; {@code OUTDOOR_AREA_NAME_INVALID}. */
    public void update(OutdoorAreaType type, String name, boolean active, Instant now) {
        this.type = type;
        this.name = Texts.required(name, 100, "OUTDOOR_AREA_NAME_INVALID");
        this.active = active;
        this.updatedAt = now;
    }

    /** Identifier of the outdoor area. */
    public UUID id() {
        return id;
    }

    /** Organization owning the site. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The site of the outdoor area. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** Kind of outdoor area. */
    public OutdoorAreaType type() {
        return type;
    }

    /** Name, unique in the site. */
    public String name() {
        return name;
    }

    /** Whether the area is in use; never deleted (M2). */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
