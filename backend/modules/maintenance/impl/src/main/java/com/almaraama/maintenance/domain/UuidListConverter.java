package com.almaraama.maintenance.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Stores a short list of identifiers as comma-separated text (families of a type, designated technicians). */
@Converter
public class UuidListConverter implements AttributeConverter<List<UUID>, String> {

    /** The text of the list; empty for an empty list. */
    @Override
    public String convertToDatabaseColumn(List<UUID> ids) {
        return ids == null ? "" : String.join(",", ids.stream().map(UUID::toString).toList());
    }

    /** The list of the text. */
    @Override
    public List<UUID> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.split(",")).map(String::strip).filter(s -> !s.isEmpty()).map(UUID::fromString)
                .toList();
    }
}
