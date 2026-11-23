package com.almaraama.membership.domain;

import com.almaraama.shared.access.Permission;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores a set of permissions as their codes separated by commas, in a plain text column. */
@Converter
public class PermissionSetConverter implements AttributeConverter<Set<Permission>, String> {

    /** Permission codes, sorted and comma-separated. */
    @Override
    public String convertToDatabaseColumn(Set<Permission> permissions) {
        return permissions == null ? "" : permissions.stream().map(Permission::code).sorted()
                .collect(Collectors.joining(","));
    }

    /** Back to permissions; unknown codes are ignored. */
    @Override
    public Set<Permission> convertToEntityAttribute(String column) {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        if (column != null && !column.isBlank()) {
            Arrays.stream(column.split(",")).map(String::strip)
                    .forEach(code -> Permission.fromCode(code).ifPresent(permissions::add));
        }
        return permissions;
    }
}
