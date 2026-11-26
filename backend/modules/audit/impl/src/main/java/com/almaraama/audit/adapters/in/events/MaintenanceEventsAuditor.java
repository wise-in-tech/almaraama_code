package com.almaraama.audit.adapters.in.events;

import com.almaraama.assets.api.events.AssetArchivedV1;
import com.almaraama.assets.api.events.AssetQrRotatedV1;
import com.almaraama.audit.domain.AuditEntry;
import com.almaraama.audit.domain.AuditJournal;
import com.almaraama.maintenance.api.events.IssueTypeChangedV1;
import com.almaraama.maintenance.api.events.SiteIssueTypeConfigChangedV1;
import com.almaraama.workflow.api.events.WorkflowBindingChangedV1;
import com.almaraama.workflow.api.events.WorkflowVersionPublishedV1;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records the sensitive changes of sprint 3 (C10, C12, ADR-036, ADR-037, ADR-039): the circuit and options of a
 * client, published circuit versions, routing and visibility of issue types, issue types themselves, archived
 * equipment and replaced QR codes. Values before and after, never personal data beyond identifiers. Runs after the
 * transaction committed; retried from the outbox (CF-6).
 */
@Component
class MaintenanceEventsAuditor {

    private final AuditJournal journal;

    /** @param journal the append-only journal */
    MaintenanceEventsAuditor(AuditJournal journal) {
        this.journal = journal;
    }

    /** Journals {@code WORKFLOW_BINDING_CHANGED} with the circuit and options before and after (H5). */
    @ApplicationModuleListener
    void on(WorkflowBindingChangedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "WORKFLOW_BINDING_CHANGED", "ORGANIZATION",
                event.organizationId().value(), Map.of("before", event.before(), "after", event.after())));
    }

    /** Journals {@code WORKFLOW_VERSION_PUBLISHED} (ADR-036). */
    @ApplicationModuleListener
    void on(WorkflowVersionPublishedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "WORKFLOW_VERSION_PUBLISHED", "WORKFLOW",
                event.workflowId(), Map.of("code", event.workflowCode(), "number", String.valueOf(event.number()))));
    }

    /** Journals {@code ISSUE_TYPE_CONFIG_CHANGED} with routing and visibility before and after (H4, H8). */
    @ApplicationModuleListener
    void on(SiteIssueTypeConfigChangedV1 event) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("siteId", event.siteId().value().toString());
        details.put("before", event.before());
        details.put("after", event.after());
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ISSUE_TYPE_CONFIG_CHANGED", "ISSUE_TYPE",
                event.issueTypeId(), details));
    }

    /** Journals {@code ISSUE_TYPE_CHANGED} for a category or a type (H2, M7). */
    @ApplicationModuleListener
    void on(IssueTypeChangedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ISSUE_TYPE_CHANGED", event.kind(),
                event.id(), Map.of("code", event.code(), "active", String.valueOf(event.active()))));
    }

    /** Journals {@code ASSET_ARCHIVED} (M2). */
    @ApplicationModuleListener
    void on(AssetArchivedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ASSET_ARCHIVED", "ASSET", event.assetId(),
                Map.of("siteId", event.siteId().value().toString())));
    }

    /** Journals {@code ASSET_QR_ROTATED} (G4). */
    @ApplicationModuleListener
    void on(AssetQrRotatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ASSET_QR_ROTATED", "ASSET", event.assetId(),
                Map.of("siteId", event.siteId().value().toString())));
    }
}
