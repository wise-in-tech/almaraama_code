package com.almaraama.workflow.domain;

import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.workflow.api.NodeCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A step of a circuit version (ADR-036): {@code STATE} in P1, with a fixed category and a JSON configuration. */
@Entity
@Table(schema = "workflow", name = "workflow_node")
public class WorkflowNode {

    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "node_type", nullable = false)
    private String nodeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private NodeCategory category;

    @Column(name = "label_ar")
    private String labelAr;

    @Column(name = "label_en")
    private String labelEn;

    @Column(name = "label_fr")
    private String labelFr;

    @Column(name = "config", nullable = false)
    private String config;

    /** For JPA only. */
    protected WorkflowNode() {
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The version. */
    public UUID versionId() {
        return versionId;
    }

    /** Step code. */
    public String code() {
        return code;
    }

    /** Kind of step ({@code STATE} in P1). */
    public String nodeType() {
        return nodeType;
    }

    /** Fixed category. */
    public NodeCategory category() {
        return category;
    }

    /** Label of the circuit, with the fallback of M6. */
    public LocalizedText label() {
        return new LocalizedText(labelAr, labelEn, labelFr).filled(code);
    }

    /** Configuration as JSON text, for example {@code {"autoClose":{"transition":"auto_close"}}}. */
    public String configJson() {
        return config;
    }
}
