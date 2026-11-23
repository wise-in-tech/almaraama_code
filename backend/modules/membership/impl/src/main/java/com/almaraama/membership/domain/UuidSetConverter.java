package com.almaraama.membership.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stores a small set of identifiers (allowed buildings) separated by commas, in a plain text column. */
@Converter
public class UuidSetConverter implements AttributeConverter<Set<UUID>, String> {

    /** Identifiers, sorted and comma-separated. */
    @Override
    public String convertToDatabaseColumn(Set<UUID> ids) {
        return ids == null ? "" : ids.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    }

    /** Back to identifiers. */
    @Override
    public Set<UUID> convertToEntityAttribute(String column) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (column != null && !column.isBlank()) {
            Arrays.stream(column.split(",")).map(String::strip).map(UUID::fromString).forEach(ids::add);
        }
        return ids;
    }
}
