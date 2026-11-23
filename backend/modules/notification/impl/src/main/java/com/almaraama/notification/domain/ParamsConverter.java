package com.almaraama.notification.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Stores the text parameters as {@code key=value&...}, URL-encoded so that no separator can appear inside a value. */
@Converter
public class ParamsConverter implements AttributeConverter<Map<String, String>, String> {

    /** {@code key=value} pairs joined with {@code &}, URL-encoded. */
    @Override
    public String convertToDatabaseColumn(Map<String, String> params) {
        return params == null ? "" : params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    /** Back to an ordered map. */
    @Override
    public Map<String, String> convertToEntityAttribute(String column) {
        Map<String, String> params = new LinkedHashMap<>();
        if (column != null && !column.isBlank()) {
            for (String pair : column.split("&")) {
                String[] parts = pair.split("=", 2);
                params.put(decode(parts[0]), parts.length > 1 ? decode(parts[1]) : "");
            }
        }
        return params;
    }

    /** URL-encodes a key or value. */
    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** Decodes a key or value. */
    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
