package com.almaraama.customization.api;

import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.i18n.LocalizedText;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One characteristic of an equipment family (G3, ADR-039), as other modules see it.
 *
 * @param scope          {@code PLATFORM} or {@code ORGANIZATION}
 * @param organizationId the owner of an organization's characteristic
 * @param label          filled in every language (M6 fallback)
 * @param options        choices of a {@code LIST}, archived ones included
 */
public record AttributeDefinitionView(UUID id, ScopeType scope, Optional<UUID> organizationId, UUID familyId,
                                      String code, AttributeDataType dataType, boolean required, LocalizedText label,
                                      Optional<String> unit, List<Option> options, int sortOrder, boolean active,
                                      long version) {

    /** A choice of a {@code LIST}. */
    public record Option(String code, LocalizedText label, boolean active) {
    }
}
