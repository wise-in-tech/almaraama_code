package com.almaraama.assets.application;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Characteristics of an equipment as JSON text (G3, ADR-039): the domain stores the text already validated, the use
 * cases read and write it here. Only strings, numbers and booleans, keyed by definition code.
 */
final class AttributeJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {
    };

    private AttributeJson() {
    }

    /** The values of a stored text; an unreadable text gives no value (never expected). */
    static Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, MAP);
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    /** The text of validated values. */
    static String write(Map<String, Object> values) {
        return JSON.writeValueAsString(values == null ? Map.of() : values);
    }
}
