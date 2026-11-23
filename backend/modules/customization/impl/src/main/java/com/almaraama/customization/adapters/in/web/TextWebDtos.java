package com.almaraama.customization.adapters.in.web;

import com.almaraama.customization.domain.TextEntry;
import com.almaraama.customization.domain.TextOrigin;
import com.almaraama.shared.i18n.Language;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response bodies of the text dictionary (contract {@code Text*} schemas, ADR-029). */
final class TextWebDtos {

    /** Holder of the records only. */
    private TextWebDtos() {
    }

    /** The texts of one language (contract {@code TextBundle}). */
    record TextBundle(Language language, String version, Map<String, String> texts) {
    }

    /** A person named in a response. */
    record PersonRef(UUID id, String firstName, String lastName) {
    }

    /** One text of one language (contract {@code TextValue}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TextValue(String key, Language language, String text, TextOrigin origin, String seedText, long version,
                     PersonRef updatedBy, Instant updatedAt) {

        /** From an entry, with the name of its last editor. */
        static TextValue of(TextEntry e, Map<UUID, PersonRef> people) {
            return new TextValue(e.key(), e.language(), e.text(), e.origin(), e.seedText().orElse(null), e.version(),
                    e.updatedBy().map(people::get).orElse(null), e.updatedAt());
        }
    }

    /** One key in every language (contract {@code TextRow}). */
    record TextRow(String key, List<String> variables, List<TextValue> values) {
    }

    /** Body of {@code PUT /v1/admin/texts/{key}/{language}}. */
    record UpdateTextRequest(@NotNull @Size(min = 1, max = 2000) String text, @PositiveOrZero Long version) {
    }

    /** One change of a text (contract {@code TextRevision}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TextRevisionResponse(UUID id, String key, Language language, String oldText, String newText,
                                TextOrigin origin, String actor, PersonRef changedBy, Instant recordedAt) {
    }

    /** Pagination of a list (contract {@code PageMeta}). */
    record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    /** One page of response bodies. */
    record Page<T>(List<T> items, PageMeta page) {
    }
}
