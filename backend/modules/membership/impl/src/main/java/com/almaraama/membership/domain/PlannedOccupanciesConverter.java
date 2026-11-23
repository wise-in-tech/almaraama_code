package com.almaraama.membership.domain;

import com.almaraama.shared.id.UnitId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Stores planned units one per line, {@code unit|role|primaryTenant} (format documented in the migration). */
@Converter
public class PlannedOccupanciesConverter implements AttributeConverter<List<PlannedOccupancy>, String> {

    /** One line per planned unit. */
    @Override
    public String convertToDatabaseColumn(List<PlannedOccupancy> planned) {
        if (planned == null) {
            return "";
        }
        return planned.stream().map(p -> p.unitId().value() + "|" + p.role().name() + "|" + p.primaryTenant())
                .collect(Collectors.joining("\n"));
    }

    /** Back to the planned units. */
    @Override
    public List<PlannedOccupancy> convertToEntityAttribute(String column) {
        List<PlannedOccupancy> planned = new ArrayList<>();
        if (column == null || column.isBlank()) {
            return planned;
        }
        for (String line : column.split("\n")) {
            String[] parts = line.split("\\|", 3);
            planned.add(new PlannedOccupancy(new UnitId(UUID.fromString(parts[0])), OccupancyRole.valueOf(parts[1]),
                    Boolean.parseBoolean(parts[2])));
        }
        return planned;
    }
}
