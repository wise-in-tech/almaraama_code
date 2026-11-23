package com.almaraama.audit.adapters.in.events;

import com.almaraama.audit.domain.AuditEntry;
import com.almaraama.audit.domain.AuditJournal;
import com.almaraama.customization.api.events.TextUpdatedV1;
import com.almaraama.sites.api.events.OrganizationDeactivatedV1;
import com.almaraama.sites.api.events.OrganizationReactivatedV1;
import com.almaraama.sites.api.events.ZoneDeactivatedV1;
import com.almaraama.sites.api.events.ZoneMergedV1;
import com.almaraama.sites.api.events.ZoneValidatedV1;
import java.util.HashMap;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records the actions of the platform administrators on the reference data, the organizations and the text
 * dictionary (C12, M6, ADR-035): validation and merge of provisional zones, deactivation of organizations, edited
 * texts. Runs after the transaction committed; retried from the outbox on failure (CF-6).
 */
@Component
class ReferenceEventsAuditor {

    private final AuditJournal journal;

    /** @param journal the append-only journal */
    ReferenceEventsAuditor(AuditJournal journal) {
        this.journal = journal;
    }

    /** Journals {@code ZONE_VALIDATED} (A8). */
    @ApplicationModuleListener
    void on(ZoneValidatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ZONE_VALIDATED", "ZONE", event.zoneId(),
                Map.of("countryCode", event.countryCode(), "code", event.code())));
    }

    /** Journals {@code ZONE_MERGED} with the target and the number of moved addresses (A8). */
    @ApplicationModuleListener
    void on(ZoneMergedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ZONE_MERGED", "ZONE", event.zoneId(),
                Map.of("targetZoneId", event.targetZoneId().toString(),
                        "movedAddresses", String.valueOf(event.movedAddresses()))));
    }

    /** Journals {@code ZONE_DEACTIVATED} (A8, M2). */
    @ApplicationModuleListener
    void on(ZoneDeactivatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ZONE_DEACTIVATED", "ZONE", event.zoneId(),
                Map.of("countryCode", event.countryCode(), "code", event.code())));
    }

    /** Journals {@code ORGANIZATION_DEACTIVATED} with its reason (C12). */
    @ApplicationModuleListener
    void on(OrganizationDeactivatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ORGANIZATION_DEACTIVATED", "ORGANIZATION",
                event.organizationId().value(), reason(event.reason())));
    }

    /** Journals {@code ORGANIZATION_REACTIVATED} with its reason (C12). */
    @ApplicationModuleListener
    void on(OrganizationReactivatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ORGANIZATION_REACTIVATED", "ORGANIZATION",
                event.organizationId().value(), reason(event.reason())));
    }

    /** Journals {@code TEXT_UPDATED}: key, language and origin, never the text (M6). */
    @ApplicationModuleListener
    void on(TextUpdatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "TEXT_UPDATED", "TEXT", null,
                Map.of("key", event.key(), "language", event.language(), "origin", event.origin())));
    }

    /** The reason as a detail, when given. */
    private static Map<String, String> reason(String reason) {
        Map<String, String> details = new HashMap<>();
        if (reason != null && !reason.isBlank()) {
            details.put("reason", reason.strip());
        }
        return details;
    }
}
