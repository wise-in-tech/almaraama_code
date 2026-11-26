package com.almaraama.maintenance.adapters.in.web;

import com.almaraama.identity.api.PersonSummary;
import com.almaraama.maintenance.application.IssueTypeService.CategoryView;
import com.almaraama.maintenance.application.IssueTypeService.ConfigView;
import com.almaraama.maintenance.application.TicketService.HistoryEntry;
import com.almaraama.maintenance.application.TicketService.PhotoView;
import com.almaraama.maintenance.application.TicketService.TicketCard;
import com.almaraama.maintenance.application.TicketService.TicketDetail;
import com.almaraama.maintenance.domain.IssueType;
import com.almaraama.maintenance.domain.RoutingDestination;
import com.almaraama.maintenance.domain.Ticket;
import com.almaraama.maintenance.domain.TicketActivityType;
import com.almaraama.maintenance.domain.TicketNature;
import com.almaraama.maintenance.domain.Visibility;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.workflow.api.NodeCategory;
import com.almaraama.workflow.api.NodeState;
import com.almaraama.workflow.api.TransitionOption;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response bodies of the issue type and ticket endpoints (contract tag {@code tickets}). */
final class MaintenanceWebDtos {

    private MaintenanceWebDtos() {
    }

    /** A text in every language (contract {@code LocalizedText}). */
    record Text(String ar, String en, String fr) {
        /** From the shared value type. */
        static Text of(LocalizedText t) {
            return t == null ? null : new Text(t.ar(), t.en(), t.fr());
        }
    }

    /** A text typed in one or more languages (contract {@code LocalizedTextInput}). */
    record TextInput(@Size(min = 1, max = 100) String ar, @Size(min = 1, max = 100) String en,
                     @Size(min = 1, max = 100) String fr) {
        /** To the shared value type. */
        LocalizedText toText() {
            return new LocalizedText(ar, en, fr);
        }
    }

    /** A person (contract {@code PersonRef}). */
    record PersonRef(UUID id, String firstName, String lastName) {
        /** From identity's summary. */
        static PersonRef of(PersonSummary p) {
            return p == null ? null : new PersonRef(p.id().value(), p.firstName(), p.lastName());
        }
    }

    /** An identifier and a name (contract {@code NamedRef}). */
    record NamedRef(UUID id, String name) {
    }

    /** A page (contract {@code PageMeta}). */
    record PageMeta(int page, int size, long totalElements, int totalPages) {
        /** From a page of results. */
        static PageMeta of(PageResult<?> r) {
            return new PageMeta(r.page(), r.size(), r.totalElements(), r.totalPages());
        }
    }

    // ------------------------------------------------------------------------------------------- issue types

    /** A type (contract {@code IssueType}). */
    record IssueTypeResponse(UUID id, UUID categoryId, ScopeType scope, String code, Text name, boolean assetRequired,
                             Visibility defaultVisibility, List<UUID> assetFamilyIds, int sortOrder, boolean active,
                             long version) {
        /** From the type. */
        static IssueTypeResponse of(IssueType t) {
            return new IssueTypeResponse(t.id(), t.categoryId(), t.ownerScope(), t.code(), Text.of(t.name()),
                    t.assetRequired(), t.defaultVisibility(), t.assetFamilyIds(), t.sortOrder(), t.active(),
                    t.version());
        }
    }

    /** A category with its types (contract {@code IssueCategory}). */
    record IssueCategoryResponse(UUID id, ScopeType scope, String code, Text name, int sortOrder, boolean active,
                                 List<IssueTypeResponse> types, long version) {
        /** From a category view. */
        static IssueCategoryResponse of(CategoryView v) {
            return new IssueCategoryResponse(v.category().id(), v.category().ownerScope(), v.category().code(),
                    Text.of(v.category().name()), v.category().sortOrder(), v.category().active(),
                    v.types().stream().map(IssueTypeResponse::of).toList(), v.category().version());
        }
    }

    /** A type in the catalog of a site (contract {@code IssueCatalogType}). */
    record CatalogType(UUID id, String code, Text name, boolean assetRequired, Visibility visibility) {
    }

    /** A category in the catalog of a site (contract {@code IssueCatalogCategory}). */
    record CatalogCategory(UUID id, String code, Text name, List<CatalogType> types) {
    }

    /** Body of a new category (contract {@code IssueCategoryInput}). */
    record IssueCategoryInput(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,29}$") String code,
                              @NotNull @Valid TextInput name, @Min(0) @Max(1000) Integer sortOrder) {
    }

    /** Body of a category change (contract {@code UpdateIssueCategoryRequest}). */
    record UpdateIssueCategoryRequest(@NotNull @Valid TextInput name, @NotNull @Min(0) @Max(1000) Integer sortOrder,
                                      @NotNull Boolean active, @NotNull @Min(0) Long version) {
    }

    /** Body of a new type (contract {@code IssueTypeInput}). */
    record IssueTypeInput(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,39}$") String code,
                          @NotNull @Valid TextInput name, @NotNull Boolean assetRequired,
                          @NotNull Visibility defaultVisibility, @Size(max = 20) List<UUID> assetFamilyIds,
                          @Min(0) @Max(1000) Integer sortOrder) {
    }

    /** Body of a type change (contract {@code UpdateIssueTypeRequest}). */
    record UpdateIssueTypeRequest(@NotNull @Valid TextInput name, @NotNull Boolean assetRequired,
                                  @NotNull Visibility defaultVisibility, @NotNull @Size(max = 20) List<UUID> assetFamilyIds,
                                  @NotNull @Min(0) @Max(1000) Integer sortOrder, @NotNull Boolean active,
                                  @NotNull @Min(0) Long version) {
    }

    /** The setting of a type on a site (contract {@code IssueTypeConfig}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record IssueTypeConfigResponse(IssueTypeResponse issueType, Text categoryName, boolean configured,
                                   RoutingDestination routing, List<PersonRef> technicians, Visibility visibility,
                                   boolean active, Long version) {
        /** From a setting view. */
        static IssueTypeConfigResponse of(ConfigView v) {
            return new IssueTypeConfigResponse(IssueTypeResponse.of(v.type()), Text.of(v.category().name()),
                    v.configured(), v.routing(), v.technicians().stream().map(PersonRef::of).toList(), v.visibility(),
                    v.active(), v.version().orElse(null));
        }
    }

    /** Body of a setting (contract {@code IssueTypeConfigInput}). */
    record IssueTypeConfigInput(@NotNull RoutingDestination routing, @Size(max = 5) List<UUID> technicianIds,
                                @NotNull Visibility visibility, @NotNull Boolean active, Long version) {
    }

    // ------------------------------------------------------------------------------------------------ tickets

    /** A step (contract {@code TicketStatus}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Status(String code, NodeCategory category, Text label, Instant since, String holdReason) {
        /** From a step. */
        static Status of(NodeState s) {
            return new Status(s.code(), s.category(), Text.of(s.label()), null, null);
        }
    }

    /** An address (contract {@code TicketAddress}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Address(String label, String addressLine, String postalCode, Text zoneName) {
    }

    /** A unit (part of {@code TicketLocation}). */
    record UnitRef(UUID id, String reference) {
    }

    /** Where the problem is (contract {@code TicketLocation}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Location(NamedRef building, Integer floor, NamedRef outdoorArea, UnitRef unit, Address address) {
    }

    /** The type of a ticket (part of {@code TicketSummary}). */
    record TicketType(UUID id, String code, Text name, Text categoryName) {
    }

    /** The equipment of a ticket (part of {@code TicketSummary}). */
    record AssetRef(UUID id, String name, String reference) {
    }

    /** A ticket as listed (contract {@code TicketSummary}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TicketSummaryResponse(UUID id, String reference, UUID siteId, String siteName, TicketNature nature,
                                 TicketType issueType, Visibility visibility, Status status, Location location,
                                 AssetRef asset, boolean mine, long photoCount, Instant createdAt,
                                 Instant lastChangeAt) {
        /** From a card. */
        static TicketSummaryResponse of(TicketCard c) {
            Ticket t = c.ticket();
            Location location = new Location(c.building().map(b -> new NamedRef(b.id().value(), b.name())).orElse(null),
                    t.location().floor().orElse(null),
                    c.outdoorArea().map(o -> new NamedRef(o.id(), o.name())).orElse(null),
                    c.unit().map(u -> new UnitRef(u.id().value(), u.reference())).orElse(null),
                    c.address().map(a -> new Address(a.label().orElse(null), a.addressLine(),
                            a.postalCode().orElse(null), Text.of(a.zoneName()))).orElse(null));
            Status status = new Status(c.status().code(), c.status().category(), Text.of(c.status().label()),
                    t.statusSince(), t.holdReason().orElse(null));
            return new TicketSummaryResponse(t.id(), t.reference(), t.siteId().value(), c.siteName(), t.nature(),
                    new TicketType(c.type().id(), c.type().code(), Text.of(c.type().name()),
                            Text.of(c.category().name())), t.visibility(), status, location,
                    c.asset().map(a -> new AssetRef(a.id(), a.name(), a.reference())).orElse(null), c.mine(),
                    c.photoCount(), t.createdAt(), t.lastChangeAt());
        }
    }

    /** A signed link (contract {@code SignedFileLink}). */
    record FileLink(UUID fileId, String url, String contentType, Instant expiresAt) {
    }

    /** A photo (contract {@code TicketPhoto}). */
    record Photo(UUID id, FileLink file, Instant addedAt) {
        /** From a photo view. */
        static Photo of(PhotoView v) {
            return new Photo(v.photo().id(), new FileLink(v.link().fileId(), v.link().url(), v.link().contentType(),
                    v.link().expiresAt()), v.photo().addedAt());
        }
    }

    /** The whole ticket (contract {@code Ticket}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TicketResponse(UUID id, String reference, UUID siteId, String siteName, TicketNature nature,
                          TicketType issueType, Visibility visibility, Status status, Location location,
                          AssetRef asset, boolean mine, long photoCount, Instant createdAt, Instant lastChangeAt,
                          boolean minimized, String description, PersonRef requester, List<Photo> photos,
                          List<PersonRef> technicians, boolean canAddPhoto, long version) {
        /** From a detail; the description is shown to everyone who sees the ticket. */
        static TicketResponse of(TicketDetail d) {
            TicketSummaryResponse s = TicketSummaryResponse.of(d.card());
            Ticket t = d.card().ticket();
            return new TicketResponse(s.id(), s.reference(), s.siteId(), s.siteName(), s.nature(), s.issueType(),
                    s.visibility(), s.status(), s.location(), s.asset(), s.mine(), s.photoCount(), s.createdAt(),
                    s.lastChangeAt(), d.minimized(), t.description().orElse(null),
                    d.requester().map(PersonRef::of).orElse(null), d.photos().stream().map(Photo::of).toList(),
                    d.technicians().stream().map(PersonRef::of).toList(), d.canAddPhoto(), t.version());
        }
    }

    /** A page of tickets (contract {@code TicketPage}). */
    record TicketPage(List<TicketSummaryResponse> items, PageMeta page) {
    }

    /** Body of a new ticket (contract {@code TicketInput}). */
    record TicketInput(@NotNull UUID siteId, @NotNull UUID issueTypeId, UUID assetId, UUID buildingId,
                       @Min(-10) @Max(200) Integer floor, UUID outdoorAreaId, UUID unitId,
                       @Size(max = 2000) String description, @Size(max = 3) List<UUID> photoFileIds) {
    }

    /** The target step of a passage (part of {@code AvailableTransition}). */
    record Target(String code, NodeCategory category, Text label) {
    }

    /** A passage the reader may take (contract {@code AvailableTransition}). */
    record AvailableTransition(String code, Target to, boolean reasonRequired, List<String> reasons,
                               boolean commentRequired, List<String> commentRequiredForReasons) {
        /** From the engine's option. */
        static AvailableTransition of(TransitionOption o) {
            return new AvailableTransition(o.code(), new Target(o.to().code(), o.to().category(),
                    Text.of(o.to().label())), o.reasonRequired(), o.reasons(), o.commentRequired(),
                    o.commentRequiredForReasons());
        }
    }

    /** Body of a passage (contract {@code TransitionRequest}). */
    record TransitionRequest(@NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}$") String transition,
                             @Pattern(regexp = "^(PART|QUOTE|ACCESS|OTHER)$") String reason,
                             @Size(max = 500) String comment, @NotNull @Min(0) Long version) {
    }

    /** Body of a new photo. */
    record PhotoInput(@NotNull UUID fileId) {
    }

    /** The actor of a history entry. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HistoryActor(String part, PersonRef person) {
    }

    /** A history entry (contract {@code TicketHistoryEntry}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HistoryEntryResponse(Instant at, String kind, String transition, Status from, Status to, String reason,
                                String comment, TicketActivityType activity, HistoryActor actor) {
        /** From a history entry. */
        static HistoryEntryResponse of(HistoryEntry e) {
            return new HistoryEntryResponse(e.at(), e.step() ? "STEP" : "ACTIVITY", e.transition().orElse(null),
                    e.from().map(Status::of).orElse(null), e.to().map(Status::of).orElse(null), e.reason().orElse(null),
                    e.comment().orElse(null), e.activity().orElse(null),
                    new HistoryActor(e.part().name(), e.person().map(PersonRef::of).orElse(null)));
        }
    }
}
