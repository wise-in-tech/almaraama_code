package com.almaraama.customization.api;

import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.i18n.LocalizedText;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Characteristics defined as data (G3, ADR-012, ADR-039). The module that owns the target (assets) checks the
 * permissions; this module keeps the definitions and validates the values.
 */
public interface AttributesApi {

    /** A choice of a {@code LIST}, as typed by an administrator or a manager. */
    record OptionInput(String code, LocalizedText label, boolean active) {
    }

    /** A new characteristic; {@code organizationId} is required for scope {@code ORGANIZATION}. */
    record NewDefinition(ScopeType scope, Optional<UUID> organizationId, UUID familyId, String code,
                         AttributeDataType dataType, boolean required, LocalizedText label, Optional<String> unit,
                         List<OptionInput> options, int sortOrder) {
    }

    /** What may change on a characteristic: never its code, type, scope or family. */
    record DefinitionChange(boolean required, LocalizedText label, Optional<String> unit, List<OptionInput> options,
                            int sortOrder, boolean active, long version) {
    }

    /** The characteristics of a family: the platform's, then the organization's when given; by sort order. */
    List<AttributeDefinitionView> definitions(UUID familyId, Optional<UUID> organizationId, boolean includeArchived);

    /** One characteristic. */
    Optional<AttributeDefinitionView> find(UUID definitionId);

    /**
     * Adds a characteristic: {@code ATTRIBUTE_CODE_INVALID}, {@code ATTRIBUTE_LABEL_INVALID}, {@code
     * ATTRIBUTE_OPTIONS_INVALID} (a {@code LIST} needs one to fifty distinct options), {@code ATTRIBUTE_CODE_TAKEN}.
     */
    AttributeDefinitionView create(NewDefinition definition);

    /**
     * Changes a characteristic. An option missing from the new list is archived, never removed (M2). Same errors as
     * {@link #create}, plus {@code VERSION_CONFLICT} and {@code NOT_FOUND}.
     */
    AttributeDefinitionView update(UUID definitionId, DefinitionChange change);

    /**
     * Checks values against the active characteristics of the family for this organization and returns them cleaned
     * (texts trimmed, empty values dropped). {@code ATTRIBUTE_INVALID} with the codes at fault: unknown or archived
     * code, wrong type, missing required value, unknown option.
     */
    Map<String, Object> validate(UUID familyId, UUID organizationId, Map<String, Object> values);
}
