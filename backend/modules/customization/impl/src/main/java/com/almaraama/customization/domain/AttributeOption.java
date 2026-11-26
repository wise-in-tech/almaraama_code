package com.almaraama.customization.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import java.util.regex.Pattern;

/** A choice of a {@code LIST} characteristic (G3). Archived, never deleted (M2). */
@Entity
@Table(schema = "customization", name = "attribute_option")
public class AttributeOption {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,29}$");

    @Id
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "label_ar")
    private String labelAr;

    @Column(name = "label_en")
    private String labelEn;

    @Column(name = "label_fr")
    private String labelFr;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    private Long version;

    /** For JPA only. */
    protected AttributeOption() {
    }

    /** A new choice: {@code ATTRIBUTE_OPTIONS_INVALID} for a bad code or a missing label. */
    public static AttributeOption create(UUID definitionId, String code, LocalizedText label, int sortOrder,
                                         boolean active) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw invalid();
        }
        AttributeOption option = new AttributeOption();
        option.id = UuidV7.next();
        option.definitionId = definitionId;
        option.code = code;
        option.change(label, sortOrder, active);
        return option;
    }

    /** Renames, reorders, archives or reactivates the choice; its code never changes. */
    public void change(LocalizedText label, int sortOrder, boolean active) {
        if (label == null || !label.isPresent() || label.longerThan(100)) {
            throw invalid();
        }
        this.labelAr = label.ar();
        this.labelEn = label.en();
        this.labelFr = label.fr();
        this.sortOrder = sortOrder;
        this.active = active;
    }

    /** The definition this choice belongs to. */
    public UUID definitionId() {
        return definitionId;
    }

    /** Stored value of the choice. */
    public String code() {
        return code;
    }

    /** Label, with the fallback of M6. */
    public LocalizedText label() {
        return new LocalizedText(labelAr, labelEn, labelFr).filled(code);
    }

    /** Display order. */
    public int sortOrder() {
        return sortOrder;
    }

    /** Whether the choice may be selected. */
    public boolean active() {
        return active;
    }

    /** The error of an invalid option. */
    private static BusinessException invalid() {
        return new BusinessException("ATTRIBUTE_OPTIONS_INVALID", ErrorCategory.VALIDATION, "Invalid option");
    }
}
