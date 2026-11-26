package com.almaraama.maintenance.application;

import com.almaraama.assets.api.AssetView;
import com.almaraama.assets.api.AssetsApi;
import com.almaraama.files.api.FilePurpose;
import com.almaraama.files.api.FilesApi;
import com.almaraama.files.api.SignedLink;
import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.PersonSummary;
import com.almaraama.maintenance.api.events.TicketCreatedV1;
import com.almaraama.maintenance.api.events.TicketStatusChangedV1;
import com.almaraama.maintenance.domain.IssueCategory;
import com.almaraama.maintenance.domain.IssueType;
import com.almaraama.maintenance.domain.IssueTypeConfig;
import com.almaraama.maintenance.domain.MaintenanceRepositories.IssueTypes;
import com.almaraama.maintenance.domain.MaintenanceRepositories.TicketFilter;
import com.almaraama.maintenance.domain.MaintenanceRepositories.Tickets;
import com.almaraama.maintenance.domain.RoutingDestination;
import com.almaraama.maintenance.domain.Ticket;
import com.almaraama.maintenance.domain.TicketActivity;
import com.almaraama.maintenance.domain.TicketActivityType;
import com.almaraama.maintenance.domain.TicketAssignment;
import com.almaraama.maintenance.domain.TicketPhoto;
import com.almaraama.maintenance.domain.Visibility;
import com.almaraama.membership.api.PerimeterApi;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.AddressSummary;
import com.almaraama.sites.api.BuildingSummary;
import com.almaraama.sites.api.OutdoorAreaSummary;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import com.almaraama.sites.api.UnitSummary;
import com.almaraama.workflow.api.DueTransition;
import com.almaraama.workflow.api.FireInput;
import com.almaraama.workflow.api.FireResult;
import com.almaraama.workflow.api.InstanceState;
import com.almaraama.workflow.api.NodeState;
import com.almaraama.workflow.api.StepRecord;
import com.almaraama.workflow.api.TransitionContext;
import com.almaraama.workflow.api.TransitionOption;
import com.almaraama.workflow.api.WorkflowApi;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
 * Tickets (H1 to H10, ADR-036, ADR-037): report (with routing, H4), list and detail with visibility by relations
 * (H8, M3), passages of the circuit (H5 to H7, C6), history (H9), photos (H10) and automatic closing (H5 option).
 */
@Service
public class TicketService {

    private static final Logger LOG = LoggerFactory.getLogger(TicketService.class);

    /** What a person reports (contract {@code TicketInput}). */
    public record ReportCommand(SiteId siteId, UUID issueTypeId, Optional<UUID> assetId, Optional<UUID> buildingId,
                                Optional<Integer> floor, Optional<UUID> outdoorAreaId, Optional<UUID> unitId,
                                String description, List<UUID> photoFileIds) {
    }

    /** A ticket with its labels, as listed. */
    public record TicketCard(Ticket ticket, String siteName, IssueType type, IssueCategory category,
                             NodeState status, Optional<BuildingSummary> building,
                             Optional<OutdoorAreaSummary> outdoorArea, Optional<UnitSummary> unit,
                             Optional<AssetView> asset, Optional<AddressSummary> address, long photoCount,
                             boolean mine) {
    }

    /** A photo with a link valid 5 minutes. */
    public record PhotoView(TicketPhoto photo, SignedLink link) {
    }

    /** The whole ticket as this reader may see it. */
    public record TicketDetail(TicketCard card, boolean minimized, Optional<PersonSummary> requester,
                               List<PhotoView> photos, List<PersonSummary> technicians, boolean canAddPhoto) {
    }

    /** The actor's part in a history entry. */
    public enum Part { REQUESTER, MANAGER, TECHNICIAN, SYSTEM, OTHER }

    /** One history entry (H9). */
    public record HistoryEntry(Instant at, boolean step, Optional<String> transition, Optional<NodeState> from,
                               Optional<NodeState> to, Optional<String> reason, Optional<String> comment,
                               Optional<TicketActivityType> activity, Part part, Optional<PersonSummary> person) {
    }

    private final Tickets tickets;
    private final IssueTypes issueTypes;
    private final IssueTypeService issueTypeService;
    private final WorkflowApi workflow;
    private final SitesApi sites;
    private final AssetsApi assets;
    private final FilesApi files;
    private final PerimeterApi perimeters;
    private final IdentityApi identity;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param tickets          tickets and their parts
     * @param issueTypes       categories and types
     * @param issueTypeService visibility and routing on a site
     * @param workflow         the circuit engine
     * @param sites            sites, buildings, outdoor areas, units, addresses
     * @param assets           equipment
     * @param files            photos
     * @param perimeters       recipients and designated technicians
     * @param identity         names of people
     * @param events           publication of the ticket events
     * @param clock            time of changes
     */
    TicketService(Tickets tickets, IssueTypes issueTypes, IssueTypeService issueTypeService, WorkflowApi workflow,
                  SitesApi sites, AssetsApi assets, FilesApi files, PerimeterApi perimeters, IdentityApi identity,
                  ApplicationEventPublisher events, Clock clock) {
        this.tickets = tickets;
        this.issueTypes = issueTypes;
        this.issueTypeService = issueTypeService;
        this.workflow = workflow;
        this.sites = sites;
        this.assets = assets;
        this.files = files;
        this.perimeters = perimeters;
        this.identity = identity;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------ report

    /**
     * H1 to H4, H10, criterion 8 and 9: reports a problem; needs {@code ticket:create} on the site. Location copied
     * from the equipment (G2); a unit occupied by the requester unless {@code unit:read}; an allowed building (C7);
     * the equipment when the type requires it (H3); three photos at most. The circuit starts and the routing assigns
     * the designated technicians. Errors: see the contract's {@code TicketRuleViolated}.
     */
    @Transactional
    public TicketDetail report(Perimeter perimeter, ReportCommand command) {
        SiteSummary site = sites.findSite(command.siteId()).orElseThrow(Errors::notFound);
        perimeter.require(Permission.TICKET_CREATE, site.organizationId(), site.id());
        IssueType type = issueTypes.type(command.issueTypeId()).filter(t -> t.usableBy(site.organizationId()))
                .orElseThrow(() -> Errors.rule("ISSUE_TYPE_NOT_IN_SCOPE"));
        Optional<IssueTypeConfig> config = issueTypeService.configOn(site.id(), type.id());
        if (!type.active() || config.map(c -> !c.active()).orElse(false)) {
            throw Errors.rule("ISSUE_TYPE_ARCHIVED");
        }
        Optional<AssetView> asset = command.assetId().map(id -> assets.findAsset(id)
                .filter(a -> a.siteId().equals(site.id())).orElseThrow(() -> Errors.rule("ASSET_NOT_IN_SITE")));
        if (asset.filter(AssetView::archived).isPresent()) {
            throw Errors.rule("ASSET_ARCHIVED");
        }
        if (type.assetRequired() && asset.isEmpty()) {
            throw Errors.rule("TICKET_ASSET_REQUIRED");
        }
        Ticket.Location location = asset.map(a -> new Ticket.Location(a.buildingId(), a.floor(), a.outdoorAreaId(),
                Optional.empty())).orElseGet(() -> location(perimeter, site, command));
        location.buildingId().ifPresent(b -> {
            if (!perimeter.canSeeBuilding(site.organizationId(), site.id(), b)) {
                throw Errors.rule("BUILDING_NOT_ALLOWED");
            }
        });
        List<UUID> photos = command.photoFileIds() == null ? List.of() : command.photoFileIds().stream().distinct()
                .toList();
        if (photos.size() > TicketPhoto.MAX_PER_TICKET) {
            throw Errors.rule("TICKET_PHOTO_LIMIT");
        }
        Instant now = clock.instant();
        Actor requester = perimeter.actor();
        Ticket ticket = Ticket.report(site.organizationId(), site.id(), tickets.nextReference(site.organizationId()),
                type.id(), location, asset.map(AssetView::id), requester, issueTypeService.visibilityOn(site.id(), type),
                command.description(), now);
        tickets.save(ticket);
        for (UUID fileId : photos) {
            files.attach(fileId, requester, FilePurpose.TICKET_PHOTO, "maintenance", ticket.id());
            tickets.save(TicketPhoto.of(ticket.id(), fileId, requester.id(), now));
        }
        InstanceState state = workflow.start(WorkflowApi.TICKET, ticket.id(), site.organizationId(), site.id(),
                requester);
        ticket.enterCircuit(state.instanceId(), state.versionId(), state.node().code(),
                state.node().category().name(), state.enteredAt());
        tickets.save(ticket);
        tickets.save(TicketActivity.of(ticket.id(), TicketActivityType.CREATED, requester, null, now));
        List<PersonId> routed = route(ticket, config, site);
        List<PersonId> managers = perimeters.holders(Permission.TICKET_READ_PRIVATE, site.organizationId(), site.id(),
                true).stream().filter(p -> !p.value().equals(requester.id())).toList();
        events.publishEvent(new TicketCreatedV1(ticket.id(), ticket.reference(), site.organizationId(), site.id(),
                site.name(), ticket.visibility() == Visibility.COMMON, requester, managers, routed, now));
        LOG.debug("Ticket {} reported on site {} (type {}, {} routed)", ticket.reference(), site.id().value(),
                type.code(), routed.size());
        return detail(perimeter, ticket);
    }

    /**
     * H4: the designated technicians of the type on the site who still hold {@code ticket:update-status} there; none
     * left, the ticket stays with the managers.
     */
    private List<PersonId> route(Ticket ticket, Optional<IssueTypeConfig> config, SiteSummary site) {
        if (config.filter(c -> c.routing() == RoutingDestination.TECHNICIANS).isEmpty()) {
            return List.of();
        }
        Actor system = Actor.platform();
        List<PersonId> routed = new ArrayList<>();
        for (UUID technician : config.get().technicianIds()) {
            PersonId person = new PersonId(technician);
            if (perimeters.perimeterOf(person).can(Permission.TICKET_UPDATE_STATUS, site.organizationId(), site.id())) {
                tickets.save(TicketAssignment.routed(ticket.id(), person, system, ticket.createdAt()));
                tickets.save(TicketActivity.of(ticket.id(), TicketActivityType.TECHNICIAN_ROUTED, system, technician,
                        ticket.createdAt()));
                routed.add(person);
            }
        }
        return routed;
    }

    /** The location typed without equipment, checked against the site. */
    private Ticket.Location location(Perimeter perimeter, SiteSummary site, ReportCommand command) {
        Optional<UnitSummary> unit = command.unitId().map(id -> sites.findUnit(new UnitId(id))
                .filter(u -> u.siteId().equals(site.id()) && u.active())
                .orElseThrow(() -> Errors.rule("UNIT_NOT_IN_SITE")));
        unit.ifPresent(u -> {
            boolean occupied = perimeter.site(site.id()).map(a -> a.occupiedUnitIds().contains(u.id())).orElse(false);
            if (!occupied && !perimeter.can(Permission.UNIT_READ, site.organizationId(), site.id())) {
                throw Errors.rule("TICKET_UNIT_NOT_OCCUPIED");
            }
        });
        Optional<BuildingId> building = command.buildingId().map(BuildingId::new)
                .or(() -> unit.flatMap(UnitSummary::buildingId));
        building.ifPresent(b -> sites.findBuilding(b).filter(s -> s.siteId().equals(site.id()))
                .orElseThrow(() -> Errors.rule("BUILDING_NOT_IN_SITE")));
        if (unit.isPresent() && command.buildingId().isPresent()
                && !unit.get().buildingId().map(b -> b.value().equals(command.buildingId().get())).orElse(false)) {
            throw Errors.rule("TICKET_LOCATION_INVALID");
        }
        command.outdoorAreaId().ifPresent(id -> sites.findOutdoorArea(id).filter(a -> a.siteId().equals(site.id()))
                .orElseThrow(() -> Errors.rule("OUTDOOR_AREA_NOT_IN_SITE")));
        return new Ticket.Location(building, command.floor(), command.outdoorAreaId(), unit.map(UnitSummary::id));
    }

    // ----------------------------------------------------------------------------------------------- reading

    /** H8, M3, criteria 10 and 13: one page of the tickets the reader may see. */
    @Transactional(readOnly = true)
    public PageResult<TicketCard> list(Perimeter perimeter, TicketFilter filter, PageQuery page) {
        PageResult<Ticket> found = tickets.search(Readers.of(perimeter), filter, page);
        return new PageResult<>(cards(perimeter, found.items()), found.page(), found.size(), found.totalElements());
    }

    /** A ticket when visible, else {@code NOT_FOUND}; minimized for a mere member (ADR-037). */
    @Transactional(readOnly = true)
    public TicketDetail get(Perimeter perimeter, UUID ticketId) {
        return detail(perimeter, visible(perimeter, ticketId));
    }

    /** H5 to H7: the passages the reader may take now; none for a mere reader (H8). */
    @Transactional(readOnly = true)
    public List<TransitionOption> transitions(Perimeter perimeter, UUID ticketId) {
        Ticket ticket = visible(perimeter, ticketId);
        return workflow.available(ticket.workflowInstanceId(), context(perimeter.actor(), perimeter, ticket));
    }

    /**
     * H5 to H7, C6: takes a passage; the engine checks C6, the permission and the conditions. The status copy moves in
     * the same transaction; the requester, managers and assigned technicians are notified, except the author (K1,
     * K3). {@code VERSION_CONFLICT}.
     */
    @Transactional
    public TicketDetail fire(Perimeter perimeter, UUID ticketId, String transition, Optional<String> reason,
                             Optional<String> comment, long version) {
        Ticket ticket = visible(perimeter, ticketId);
        Errors.checkVersion(version, ticket.version());
        apply(ticket, perimeter.actor(), context(perimeter.actor(), perimeter, ticket), transition,
                new FireInput(reason, comment));
        return detail(perimeter, ticket);
    }

    /** H9: steps and other changes, oldest first; people hidden in the minimized view. */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(Perimeter perimeter, UUID ticketId) {
        Ticket ticket = visible(perimeter, ticketId);
        Set<PersonId> technicians = tickets.assignments(ticketId).stream().map(TicketAssignment::technicianId)
                .collect(Collectors.toSet());
        boolean minimized = Readers.minimized(perimeter, ticket, technicians.contains(new PersonId(perimeter.actor().id())));
        List<StepRecord> steps = workflow.history(ticket.workflowInstanceId());
        List<TicketActivity> activities = tickets.activities(ticketId);
        Map<PersonId, PersonSummary> people = minimized ? Map.of() : people(java.util.stream.Stream.concat(
                steps.stream().map(StepRecord::actor), activities.stream().map(TicketActivity::actor))
                .filter(a -> a.type() == ActorType.PERSON).map(a -> new PersonId(a.id())).distinct().toList());
        List<HistoryEntry> entries = new ArrayList<>();
        for (StepRecord step : steps) {
            entries.add(new HistoryEntry(step.at(), true, step.transitionCode(), step.from(), Optional.of(step.to()),
                    step.reason(), minimized ? Optional.empty() : step.comment(), Optional.empty(),
                    part(ticket, step.actor(), technicians), person(step.actor(), people)));
        }
        for (TicketActivity activity : activities) {
            entries.add(new HistoryEntry(activity.occurredAt(), false, Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(activity.type()),
                    part(ticket, activity.actor(), technicians), person(activity.actor(), people)));
        }
        entries.sort(Comparator.comparing(HistoryEntry::at));
        return entries;
    }

    /**
     * H10: adds a photo; the requester, or a person with {@code ticket:update-status} who sees the ticket, while it is
     * open. {@code TICKET_CLOSED}, {@code TICKET_PHOTO_LIMIT}, {@code FILE_NOT_USABLE}, {@code PERMISSION_DENIED}.
     */
    @Transactional
    public TicketDetail addPhoto(Perimeter perimeter, UUID ticketId, UUID fileId) {
        Ticket ticket = visible(perimeter, ticketId);
        if (!mayAddPhoto(perimeter, ticket)) {
            throw new com.almaraama.shared.error.BusinessException("PERMISSION_DENIED",
                    com.almaraama.shared.error.ErrorCategory.PERMISSION_DENIED, "Requester or staff only");
        }
        if (ticket.ended()) {
            throw Errors.rule("TICKET_CLOSED");
        }
        if (tickets.photos(ticketId).size() >= TicketPhoto.MAX_PER_TICKET) {
            throw Errors.rule("TICKET_PHOTO_LIMIT");
        }
        Instant now = clock.instant();
        files.attach(fileId, perimeter.actor(), FilePurpose.TICKET_PHOTO, "maintenance", ticketId);
        tickets.save(TicketPhoto.of(ticketId, fileId, perimeter.actor().id(), now));
        tickets.save(TicketActivity.of(ticketId, TicketActivityType.PHOTO_ADDED, perimeter.actor(), null, now));
        ticket.touch(now);
        tickets.save(ticket);
        return detail(perimeter, ticket);
    }

    // ------------------------------------------------------------------------------------------- auto-close

    /** H5 option, criterion 19: the automatic passages due now, for the job. */
    @Transactional(readOnly = true)
    public List<DueTransition> dueAutomaticTransitions(int limit) {
        return workflow.dueAutomaticTransitions(clock.instant(), limit).stream()
                .filter(d -> WorkflowApi.TICKET.equals(d.subjectType())).toList();
    }

    /**
     * H5, H7: takes one automatic passage as the system, through the same path as a person; the requester is
     * notified. Silently skipped when the ticket moved meanwhile.
     */
    @Transactional
    public void takeAutomatic(DueTransition due) {
        Optional<Ticket> found = tickets.findById(due.subjectId());
        if (found.isEmpty() || found.get().ended()) {
            return;
        }
        Ticket ticket = found.get();
        Actor system = Actor.platform();
        TransitionContext context = new TransitionContext(system, false,
                ticket.requester().type() == ActorType.PERSON, code -> false);
        apply(ticket, system, context, due.transitionCode(), FireInput.none());
        LOG.info("Ticket {} closed automatically", ticket.reference());
    }

    // ------------------------------------------------------------------------------------------- internals

    /** Fires the passage, moves the copy, records and publishes. */
    private void apply(Ticket ticket, Actor actor, TransitionContext context, String transition, FireInput input) {
        FireResult result = workflow.fire(ticket.workflowInstanceId(), transition, context, input);
        if (result.moved()) {
            ticket.moveTo(result.to().code(), result.to().category().name(), input.reason(), result.at());
        } else {
            ticket.touch(result.at());
        }
        tickets.save(ticket);
        SiteSummary site = sites.findSite(ticket.siteId()).orElseThrow();
        Optional<PersonId> requester = ticket.requester().type() == ActorType.PERSON
                ? Optional.of(new PersonId(ticket.requester().id())).filter(p -> !p.value().equals(actor.id()))
                : Optional.empty();
        List<PersonId> managers = perimeters.holders(Permission.TICKET_READ_PRIVATE, ticket.organizationId(),
                ticket.siteId(), true).stream().filter(p -> !p.value().equals(actor.id()))
                .filter(p -> requester.map(r -> !r.equals(p)).orElse(true)).toList();
        List<PersonId> technicians = tickets.assignments(ticket.id()).stream().map(TicketAssignment::technicianId)
                .filter(p -> !p.value().equals(actor.id())).distinct().toList();
        events.publishEvent(new TicketStatusChangedV1(ticket.id(), ticket.reference(), ticket.organizationId(),
                ticket.siteId(), site.name(), transition, result.from().code(), result.from().category().name(),
                result.to().code(), result.to().category().name(), actor, requester, managers, technicians,
                result.at()));
    }

    /** What the engine needs to know about the actor (ADR-036). */
    private static TransitionContext context(Actor actor, Perimeter perimeter, Ticket ticket) {
        return new TransitionContext(actor, ticket.requestedBy(actor), ticket.requester().type() == ActorType.PERSON,
                code -> Permission.fromCode(code)
                        .map(p -> perimeter.can(p, ticket.organizationId(), ticket.siteId())).orElse(false));
    }

    /** The ticket when visible to the reader, else {@code NOT_FOUND} (M3). */
    private Ticket visible(Perimeter perimeter, UUID ticketId) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(Errors::notFound);
        boolean assigned = tickets.assigned(ticketId, new PersonId(perimeter.actor().id()));
        if (!Readers.visible(perimeter, ticket, assigned)) {
            throw Errors.notFound();
        }
        return ticket;
    }

    /** The requester, or a person with {@code ticket:update-status} on the site. */
    private static boolean mayAddPhoto(Perimeter perimeter, Ticket ticket) {
        return ticket.requestedBy(perimeter.actor())
                || perimeter.can(Permission.TICKET_UPDATE_STATUS, ticket.organizationId(), ticket.siteId());
    }

    /** The whole ticket as the reader may see it. */
    private TicketDetail detail(Perimeter perimeter, Ticket ticket) {
        List<TicketAssignment> assignments = tickets.assignments(ticket.id());
        boolean assigned = assignments.stream()
                .anyMatch(a -> a.technicianId().value().equals(perimeter.actor().id()));
        boolean minimized = Readers.minimized(perimeter, ticket, assigned);
        TicketCard card = cards(perimeter, List.of(ticket)).getFirst();
        if (minimized) {
            return new TicketDetail(card, true, Optional.empty(), List.of(), List.of(), false);
        }
        List<TicketPhoto> photos = tickets.photos(ticket.id());
        Map<UUID, SignedLink> links = files.signedLinks(photos.stream().map(TicketPhoto::fileId).toList());
        List<PersonId> ids = new ArrayList<>(assignments.stream().map(TicketAssignment::technicianId).toList());
        if (ticket.requester().type() == ActorType.PERSON) {
            ids.add(new PersonId(ticket.requester().id()));
        }
        Map<PersonId, PersonSummary> people = people(ids);
        return new TicketDetail(card, false,
                ticket.requester().type() == ActorType.PERSON
                        ? Optional.ofNullable(people.get(new PersonId(ticket.requester().id()))) : Optional.empty(),
                photos.stream().filter(p -> links.containsKey(p.fileId()))
                        .map(p -> new PhotoView(p, links.get(p.fileId()))).toList(),
                assignments.stream().map(a -> people.get(a.technicianId())).filter(java.util.Objects::nonNull).toList(),
                !ticket.ended() && mayAddPhoto(perimeter, ticket) && photos.size() < TicketPhoto.MAX_PER_TICKET);
    }

    /** Cards of a page with every label loaded in a few calls. */
    private List<TicketCard> cards(Perimeter perimeter, List<Ticket> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<SiteId, SiteSummary> siteById = sites.findSites(list.stream().map(Ticket::siteId).distinct().toList())
                .stream().collect(Collectors.toMap(SiteSummary::id, Function.identity()));
        Map<UUID, IssueType> typeById = issueTypes.types(list.stream().map(Ticket::issueTypeId).distinct().toList())
                .stream().collect(Collectors.toMap(IssueType::id, Function.identity()));
        Map<UUID, IssueCategory> categoryById = new HashMap<>();
        typeById.values().stream().map(IssueType::categoryId).distinct()
                .forEach(id -> issueTypes.category(id).ifPresent(c -> categoryById.put(id, c)));
        List<BuildingId> buildingIds = list.stream().map(t -> t.location().buildingId()).flatMap(Optional::stream)
                .distinct().toList();
        Map<BuildingId, BuildingSummary> buildingById = sites.findBuildings(buildingIds).stream()
                .collect(Collectors.toMap(BuildingSummary::id, Function.identity()));
        Map<UUID, OutdoorAreaSummary> areaById = sites.findOutdoorAreas(list.stream()
                .map(t -> t.location().outdoorAreaId()).flatMap(Optional::stream).distinct().toList()).stream()
                .collect(Collectors.toMap(OutdoorAreaSummary::id, Function.identity()));
        Map<UnitId, UnitSummary> unitById = sites.findUnits(list.stream().map(t -> t.location().unitId())
                .flatMap(Optional::stream).distinct().toList()).stream()
                .collect(Collectors.toMap(UnitSummary::id, Function.identity()));
        Map<UUID, AssetView> assetById = assets.findAssets(list.stream().map(Ticket::assetId).flatMap(Optional::stream)
                .distinct().toList()).stream().collect(Collectors.toMap(AssetView::id, Function.identity()));
        Map<UUID, AddressSummary> addresses = sites.currentAddresses(buildingIds,
                list.stream().map(Ticket::siteId).distinct().toList());
        Map<UUID, Long> photoCounts = tickets.photoCounts(list.stream().map(Ticket::id).toList());
        Map<String, Map<String, LocalizedText>> labels = new HashMap<>();
        List<TicketCard> cards = new ArrayList<>();
        for (Ticket t : list) {
            IssueType type = typeById.get(t.issueTypeId());
            Map<String, LocalizedText> versionLabels = labels.computeIfAbsent(
                    t.workflowVersionId() + ":" + t.organizationId().value(),
                    k -> workflow.labels(t.workflowVersionId(), t.organizationId()));
            NodeState status = new NodeState(t.nodeCode(),
                    com.almaraama.workflow.api.NodeCategory.valueOf(t.category()),
                    versionLabels.getOrDefault(t.nodeCode(), LocalizedText.same(t.nodeCode())));
            Optional<AddressSummary> address = t.location().buildingId().map(b -> addresses.get(b.value()))
                    .or(() -> Optional.ofNullable(addresses.get(t.siteId().value())));
            cards.add(new TicketCard(t, siteById.get(t.siteId()).name(), type, categoryById.get(type.categoryId()),
                    status, t.location().buildingId().map(buildingById::get),
                    t.location().outdoorAreaId().map(areaById::get), t.location().unitId().map(unitById::get),
                    t.assetId().map(assetById::get), address, photoCounts.getOrDefault(t.id(), 0L),
                    t.requestedBy(perimeter.actor())));
        }
        return cards;
    }

    /** The actor's part in the ticket. */
    private static Part part(Ticket ticket, Actor actor, Set<PersonId> technicians) {
        if (actor.type() == ActorType.SYSTEM) {
            return Part.SYSTEM;
        }
        if (ticket.requestedBy(actor)) {
            return Part.REQUESTER;
        }
        if (technicians.contains(new PersonId(actor.id()))) {
            return Part.TECHNICIAN;
        }
        return Part.MANAGER;
    }

    /** The person of a history entry, when known and not minimized. */
    private static Optional<PersonSummary> person(Actor actor, Map<PersonId, PersonSummary> people) {
        return actor.type() == ActorType.PERSON ? Optional.ofNullable(people.get(new PersonId(actor.id())))
                : Optional.empty();
    }

    /** People by identifier. */
    private Map<PersonId, PersonSummary> people(Collection<PersonId> ids) {
        return ids.isEmpty() ? Map.of() : identity.findPersons(ids).stream()
                .collect(Collectors.toMap(PersonSummary::id, Function.identity(), (a, b) -> a));
    }
}
