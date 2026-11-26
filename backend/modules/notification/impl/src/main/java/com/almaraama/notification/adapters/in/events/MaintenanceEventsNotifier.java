package com.almaraama.notification.adapters.in.events;

import com.almaraama.maintenance.api.events.TicketCreatedV1;
import com.almaraama.maintenance.api.events.TicketStatusChangedV1;
import com.almaraama.notification.application.Notifications;
import com.almaraama.notification.domain.NotificationTemplate;
import com.almaraama.shared.id.PersonId;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * In-application notifications of tickets (K1, K3, K6, criterion 21, ADR-037): never an SMS, an e-mail or a phone
 * notification. Recipients come with the event, computed by the maintenance module with their real permissions. The
 * parameters carry the reference, the site and the step category, never the requester's name. Idempotent per event
 * and recipient.
 */
@Component
class MaintenanceEventsNotifier {

    private final Notifications notifications;

    /** @param notifications the in-application notifications */
    MaintenanceEventsNotifier(Notifications notifications) {
        this.notifications = notifications;
    }

    /** K1: the managers of the site; H4: the technicians designated by the routing. */
    @ApplicationModuleListener
    void on(TicketCreatedV1 event) {
        Map<String, String> params = Map.of("reference", event.reference(), "siteName", event.siteName());
        String link = "/tickets/" + event.ticketId();
        for (PersonId manager : event.managerIds()) {
            notifications.notify(manager, NotificationTemplate.TICKET_CREATED, params, link, event.siteId(),
                    "TICKET_CREATED:" + event.ticketId() + ":" + manager);
        }
        for (PersonId technician : event.routedIds()) {
            notifications.notify(technician, NotificationTemplate.TICKET_ROUTED, params, link, event.siteId(),
                    "TICKET_ROUTED:" + event.ticketId() + ":" + technician);
        }
    }

    /** K3: the requester; K1: the managers; the assigned technicians. The author of the passage is not notified. */
    @ApplicationModuleListener
    void on(TicketStatusChangedV1 event) {
        Set<PersonId> recipients = new LinkedHashSet<>();
        event.requesterId().ifPresent(recipients::add);
        recipients.addAll(event.managerIds());
        recipients.addAll(event.technicianIds());
        Map<String, String> params = Map.of("reference", event.reference(), "siteName", event.siteName(),
                "category", event.toCategory(), "transition", event.transition());
        String link = "/tickets/" + event.ticketId();
        for (PersonId recipient : recipients) {
            notifications.notify(recipient, NotificationTemplate.TICKET_STATUS_CHANGED, params, link, event.siteId(),
                    "TICKET_STATUS_CHANGED:" + event.ticketId() + ":" + event.occurredAt().toEpochMilli() + ":"
                            + recipient);
        }
    }
}
