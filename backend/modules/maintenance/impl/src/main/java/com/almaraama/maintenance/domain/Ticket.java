package com.almaraama.maintenance.domain;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A ticket (H1 to H10). Its position is held by its circuit instance (workflow module, ADR-036); {@code nodeCode} and
 * {@code category} are a copy kept in the same transaction for fast lists. Never deleted (M2).
 */
@Entity
@Table(schema = "maintenance", name = "ticket")
public class Ticket {

    /** Longest description (characters). */
    public static final int MAX_DESCRIPTION = 2000;

    /** Where the problem is (G2): a building (floor, unit), an outdoor area, or the site as a whole. */
    public record Location(Optional<BuildingId> buildingId, Optional<Integer> floor, Optional<UUID> outdoorAreaId,
                           Optional<UnitId> unitId) {

        /** A building or an outdoor area, never both; a floor only inside a building. {@code TICKET_LOCATION_INVALID}. */
        public Location {
            if ((buildingId.isPresent() && outdoorAreaId.isPresent()) || (floor.isPresent() && buildingId.isEmpty())) {
                throw new BusinessException("TICKET_LOCATION_INVALID", ErrorCategory.RULE_VIOLATION,
                        "A building or an outdoor area; a floor only in a building");
            }
        }
    }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false)
    private TicketNature nature;

    @Column(name = "issue_type_id", nullable = false)
    private UUID issueTypeId;

    @Column(name = "building_id")
    private UUID buildingId;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "outdoor_area_id")
    private UUID outdoorAreaId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requester_type", nullable = false)
    private ActorType requesterType;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    @Column(name = "description")
    private String description;

    @Column(name = "original_text")
    private String originalText;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "workflow_version_id")
    private UUID workflowVersionId;

    @Column(name = "node_code", nullable = false)
    private String nodeCode;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "status_since", nullable = false)
    private Instant statusSince;

    @Column(name = "preventive_rule_id")
    private UUID preventiveRuleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_change_at", nullable = false)
    private Instant lastChangeAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Ticket() {
    }

    /**
     * A ticket reported by a person (H1). The use case has checked the site, the type, the location and the equipment;
     * it starts the circuit next ({@link #enterCircuit}). {@code TICKET_DESCRIPTION_TOO_LONG}.
     */
    public static Ticket report(OrganizationId organization, SiteId site, String reference, UUID issueTypeId,
                                Location location, Optional<UUID> assetId, Actor requester, Visibility visibility,
                                String description, Instant now) {
        String text = description == null || description.isBlank() ? null : description.strip();
        if (text != null && text.length() > MAX_DESCRIPTION) {
            throw new BusinessException("TICKET_DESCRIPTION_TOO_LONG", ErrorCategory.VALIDATION, "Description");
        }
        Ticket ticket = new Ticket();
        ticket.id = UuidV7.next();
        ticket.organizationId = organization.value();
        ticket.siteId = site.value();
        ticket.reference = reference;
        ticket.nature = TicketNature.CORRECTIVE;
        ticket.issueTypeId = issueTypeId;
        ticket.buildingId = location.buildingId().map(BuildingId::value).orElse(null);
        ticket.floor = location.floor().orElse(null);
        ticket.outdoorAreaId = location.outdoorAreaId().orElse(null);
        ticket.unitId = location.unitId().map(UnitId::value).orElse(null);
        ticket.assetId = assetId.orElse(null);
        ticket.requesterType = requester.type();
        ticket.requesterId = requester.id();
        ticket.visibility = visibility;
        ticket.description = text;
        ticket.originalText = text;
        ticket.nodeCode = "NEW";
        ticket.category = "OPEN";
        ticket.statusSince = now;
        ticket.createdAt = now;
        ticket.lastChangeAt = now;
        return ticket;
    }

    /** Records the circuit instance started for the ticket and its first step (ADR-036). */
    public void enterCircuit(UUID instanceId, UUID versionId, String node, String nodeCategory, Instant enteredAt) {
        this.workflowInstanceId = instanceId;
        this.workflowVersionId = versionId;
        moveTo(node, nodeCategory, Optional.empty(), enteredAt);
    }

    /** Copies the new position after a passage; the reason is kept only while on hold (H5). */
    public void moveTo(String node, String nodeCategory, Optional<String> reason, Instant now) {
        this.nodeCode = node;
        this.category = nodeCategory;
        this.holdReason = "ON_HOLD".equals(nodeCategory) ? reason.orElse(null) : null;
        this.statusSince = now;
        this.lastChangeAt = now;
    }

    /** Marks another change (photo, history entry) so that lists show it first. */
    public void touch(Instant now) {
        this.lastChangeAt = now;
    }

    /** Whether the ticket has ended ({@code CLOSED} or {@code CANCELLED}). */
    public boolean ended() {
        return "CLOSED".equals(category) || "CANCELLED".equals(category);
    }

    /** Whether this actor asked for the ticket (C6, relation "his own", ADR-037). */
    public boolean requestedBy(Actor actor) {
        return requesterType == actor.type() && requesterId.equals(actor.id());
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** Organization. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** Site. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** Readable reference ({@code T-000123}). */
    public String reference() {
        return reference;
    }

    /** Corrective or preventive. */
    public TicketNature nature() {
        return nature;
    }

    /** Type of problem (H2). */
    public UUID issueTypeId() {
        return issueTypeId;
    }

    /** Where the problem is. */
    public Location location() {
        return new Location(Optional.ofNullable(buildingId).map(BuildingId::new), Optional.ofNullable(floor),
                Optional.ofNullable(outdoorAreaId), Optional.ofNullable(unitId).map(UnitId::new));
    }

    /** The equipment (H3). */
    public Optional<UUID> assetId() {
        return Optional.ofNullable(assetId);
    }

    /** Who asked: a person, or the system for a preventive ticket. */
    public Actor requester() {
        return new Actor(requesterType, requesterId);
    }

    /** H8. */
    public Visibility visibility() {
        return visibility;
    }

    /** Description, as typed. */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /** The circuit instance. */
    public UUID workflowInstanceId() {
        return workflowInstanceId;
    }

    /** The circuit version (labels of the steps). */
    public UUID workflowVersionId() {
        return workflowVersionId;
    }

    /** Current step code (copy). */
    public String nodeCode() {
        return nodeCode;
    }

    /** Current step category (copy). */
    public String category() {
        return category;
    }

    /** Reason of the hold (H5). */
    public Optional<String> holdReason() {
        return Optional.ofNullable(holdReason);
    }

    /** When the current step was entered. */
    public Instant statusSince() {
        return statusSince;
    }

    /** When reported. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Last change. */
    public Instant lastChangeAt() {
        return lastChangeAt;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
