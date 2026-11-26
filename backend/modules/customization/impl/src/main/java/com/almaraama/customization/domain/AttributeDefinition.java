package com.almaraama.customization.domain;

import com.almaraama.customization.api.AttributeDataType;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.LocalizedText;
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
import java.util.regex.Pattern;

/**
 * One characteristic of an equipment family (G3, ADR-039), defined as data: a default of the platform or an
 * organization's own. Its code and type never change; it is archived, never deleted (M2).
 */
@Entity
@Table(schema = "customization", name = "attribute_definition")
public class AttributeDefinition {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-zA-Z0-9]{0,39}$");

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_scope", nullable = false)
    private ScopeType ownerScope;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "code", nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private AttributeDataType dataType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "label_ar")
    private String labelAr;

    @Column(name = "label_en")
    private String labelEn;

    @Column(name = "label_fr")
    private String labelFr;

    @Column(name = "unit")
    private String unit;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected AttributeDefinition() {
    }

    /**
     * A new characteristic of a family ({@code organization} empty for a platform default). {@code
     * ATTRIBUTE_CODE_INVALID}, {@code ATTRIBUTE_LABEL_INVALID}, {@code ATTRIBUTE_SCOPE_INVALID}.
     */
    public static AttributeDefinition create(ScopeType scope, Optional<UUID> organization, UUID family, String code,
                                             AttributeDataType dataType, boolean required, LocalizedText label,
                                             Optional<String> unit, int sortOrder, Instant now) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BusinessException("ATTRIBUTE_CODE_INVALID", ErrorCategory.VALIDATION, "lowerCamelCase code");
        }
        if ((scope == ScopeType.ORGANIZATION) != organization.isPresent() || scope == ScopeType.SITE) {
            throw new BusinessException("ATTRIBUTE_SCOPE_INVALID", ErrorCategory.VALIDATION, "Scope and owner");
        }
        AttributeDefinition definition = new AttributeDefinition();
        definition.id = UuidV7.next();
        definition.ownerScope = scope;
        definition.organizationId = organization.orElse(null);
        definition.targetType = "ASSET";
        definition.familyId = family;
        definition.code = code;
        definition.dataType = dataType;
        definition.active = true;
        definition.createdAt = now;
        definition.change(required, label, unit, sortOrder, true, now);
        return definition;
    }

    /** Changes what may change: requirement, label, unit, order, state. {@code ATTRIBUTE_LABEL_INVALID}. */
    public void change(boolean required, LocalizedText label, Optional<String> unit, int sortOrder, boolean active,
                       Instant now) {
        if (label == null || !label.isPresent() || label.longerThan(100)) {
            throw new BusinessException("ATTRIBUTE_LABEL_INVALID", ErrorCategory.VALIDATION, "A label is needed");
        }
        this.required = required;
        this.labelAr = label.ar();
        this.labelEn = label.en();
        this.labelFr = label.fr();
        this.unit = unit.map(String::strip).filter(u -> !u.isEmpty()).map(u -> u.length() > 10 ? u.substring(0, 10) : u)
                .orElse(null);
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = now;
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** {@code PLATFORM} or {@code ORGANIZATION}. */
    public ScopeType ownerScope() {
        return ownerScope;
    }

    /** The owner of an organization's characteristic. */
    public Optional<UUID> organizationId() {
        return Optional.ofNullable(organizationId);
    }

    /** The equipment family. */
    public UUID familyId() {
        return familyId;
    }

    /** Key of the value in the equipment's characteristics. */
    public String code() {
        return code;
    }

    /** Type of the value. */
    public AttributeDataType dataType() {
        return dataType;
    }

    /** Whether a value is mandatory. */
    public boolean required() {
        return required;
    }

    /** Label, with the fallback of M6. */
    public LocalizedText label() {
        return new LocalizedText(labelAr, labelEn, labelFr).filled(code);
    }

    /** Unit shown after the value. */
    public Optional<String> unit() {
        return Optional.ofNullable(unit);
    }

    /** Display order. */
    public int sortOrder() {
        return sortOrder;
    }

    /** Whether the field is shown and accepted. */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
