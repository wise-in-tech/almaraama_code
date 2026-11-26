package com.almaraama.maintenance.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Routing (H4) and visibility (H8) of an issue type on one site, set by its managers. Glossary {@code
 * SiteIssueTypeConfig}.
 */
@Entity
@Table(schema = "maintenance", name = "issue_type_config")
public class IssueTypeConfig {

    /** Most people a type may be routed to. */
    public static final int MAX_TECHNICIANS = 5;

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "issue_type_id", nullable = false)
    private UUID issueTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing", nullable = false)
    private RoutingDestination routing;

    @Convert(converter = UuidListConverter.class)
    @Column(name = "technician_ids", nullable = false)
    private List<UUID> technicianIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected IssueTypeConfig() {
    }

    /** The first setting of a type on a site. */
    public static IssueTypeConfig create(OrganizationId organization, SiteId site, UUID issueTypeId,
                                         RoutingDestination routing, List<UUID> technicians, Visibility visibility,
                                         boolean active, Instant now) {
        IssueTypeConfig config = new IssueTypeConfig();
        config.id = UuidV7.next();
        config.organizationId = organization.value();
        config.siteId = site.value();
        config.issueTypeId = issueTypeId;
        config.change(routing, technicians, visibility, active, now);
        return config;
    }

    /**
     * Changes the setting. {@code TECHNICIANS} needs one to five distinct people, {@code SITE_MANAGER} none ({@code
     * ROUTING_INVALID}).
     */
    public void change(RoutingDestination routing, List<UUID> technicians, Visibility visibility, boolean active,
                       Instant now) {
        List<UUID> distinct = technicians.stream().distinct().toList();
        boolean valid = routing == RoutingDestination.TECHNICIANS
                ? !distinct.isEmpty() && distinct.size() <= MAX_TECHNICIANS : distinct.isEmpty();
        if (!valid) {
            throw new BusinessException("ROUTING_INVALID", ErrorCategory.VALIDATION, "Technicians of the routing");
        }
        this.routing = routing;
        this.technicianIds = distinct;
        this.visibility = visibility;
        this.active = active;
        this.updatedAt = now;
    }

    /** The type. */
    public UUID issueTypeId() {
        return issueTypeId;
    }

    /** Destination of new tickets. */
    public RoutingDestination routing() {
        return routing;
    }

    /** Designated people with {@code TECHNICIANS}. */
    public List<UUID> technicianIds() {
        return technicianIds;
    }

    /** Visibility of new tickets on this site. */
    public Visibility visibility() {
        return visibility;
    }

    /** Whether the type is offered on this site. */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
