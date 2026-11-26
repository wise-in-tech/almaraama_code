package com.almaraama.maintenance.adapters.in.web;

import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.AvailableTransition;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.HistoryEntryResponse;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.PageMeta;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.PhotoInput;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.TicketInput;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.TicketPage;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.TicketResponse;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.TicketSummaryResponse;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.TransitionRequest;
import com.almaraama.maintenance.application.TicketService;
import com.almaraama.maintenance.application.TicketService.ReportCommand;
import com.almaraama.maintenance.application.TicketService.TicketCard;
import com.almaraama.maintenance.domain.MaintenanceRepositories.TicketFilter;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.workflow.api.NodeCategory;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tickets, their passages, history and photos (contract tag {@code tickets}, H1 to H10). */
@RestController
class TicketController {

    private final TicketService tickets;

    /** @param tickets ticket use cases */
    TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    /** {@code GET /v1/tickets}: see {@link TicketService#list}. */
    @GetMapping("/v1/tickets")
    TicketPage list(Perimeter perimeter, @RequestParam(required = false) UUID siteId,
                    @RequestParam(defaultValue = "ALL") String view,
                    @RequestParam(required = false) List<NodeCategory> category,
                    @RequestParam(required = false) UUID issueTypeId, @RequestParam(required = false) UUID buildingId,
                    @RequestParam(required = false) UUID assetId, @RequestParam(required = false) String q,
                    @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        TicketFilter filter = new TicketFilter(Optional.ofNullable(siteId).map(SiteId::new), view,
                category == null ? List.of() : category.stream().map(Enum::name).toList(),
                Optional.ofNullable(issueTypeId), Optional.ofNullable(buildingId), Optional.ofNullable(assetId),
                Optional.ofNullable(q));
        PageResult<TicketCard> found = tickets.list(perimeter, filter, PageQuery.of(page, size));
        return new TicketPage(found.items().stream().map(TicketSummaryResponse::of).toList(), PageMeta.of(found));
    }

    /** {@code POST /v1/tickets}: see {@link TicketService#report}. */
    @PostMapping("/v1/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    TicketResponse report(Perimeter perimeter, @Valid @RequestBody TicketInput body) {
        return TicketResponse.of(tickets.report(perimeter, new ReportCommand(new SiteId(body.siteId()),
                body.issueTypeId(), Optional.ofNullable(body.assetId()), Optional.ofNullable(body.buildingId()),
                Optional.ofNullable(body.floor()), Optional.ofNullable(body.outdoorAreaId()),
                Optional.ofNullable(body.unitId()), body.description(),
                body.photoFileIds() == null ? List.of() : body.photoFileIds())));
    }

    /** {@code GET /v1/tickets/{ticketId}}: see {@link TicketService#get}. */
    @GetMapping("/v1/tickets/{ticketId}")
    TicketResponse get(Perimeter perimeter, @PathVariable UUID ticketId) {
        return TicketResponse.of(tickets.get(perimeter, ticketId));
    }

    /** {@code GET /v1/tickets/{ticketId}/transitions}: see {@link TicketService#transitions}. */
    @GetMapping("/v1/tickets/{ticketId}/transitions")
    List<AvailableTransition> transitions(Perimeter perimeter, @PathVariable UUID ticketId) {
        return tickets.transitions(perimeter, ticketId).stream().map(AvailableTransition::of).toList();
    }

    /** {@code POST /v1/tickets/{ticketId}/transitions}: see {@link TicketService#fire}. */
    @PostMapping("/v1/tickets/{ticketId}/transitions")
    TicketResponse fire(Perimeter perimeter, @PathVariable UUID ticketId,
                        @Valid @RequestBody TransitionRequest body) {
        return TicketResponse.of(tickets.fire(perimeter, ticketId, body.transition(),
                Optional.ofNullable(body.reason()), Optional.ofNullable(body.comment()), body.version()));
    }

    /** {@code GET /v1/tickets/{ticketId}/history}: see {@link TicketService#history}. */
    @GetMapping("/v1/tickets/{ticketId}/history")
    List<HistoryEntryResponse> history(Perimeter perimeter, @PathVariable UUID ticketId) {
        return tickets.history(perimeter, ticketId).stream().map(HistoryEntryResponse::of).toList();
    }

    /** {@code POST /v1/tickets/{ticketId}/photos}: see {@link TicketService#addPhoto}. */
    @PostMapping("/v1/tickets/{ticketId}/photos")
    TicketResponse addPhoto(Perimeter perimeter, @PathVariable UUID ticketId, @Valid @RequestBody PhotoInput body) {
        return TicketResponse.of(tickets.addPhoto(perimeter, ticketId, body.fileId()));
    }
}
