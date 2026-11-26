package com.almaraama.workflow.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** JSON text of the circuit tables (configs, conditions, options, payloads), read and written here (ADR-036). */
public final class Json {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Json() {
    }

    /** An object; blank or unreadable text gives an empty map. */
    public static Map<String, Object> object(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    /** An array of objects; {@code null} when unreadable (the publication check reports it). */
    public static List<Map<String, Object>> array(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JacksonException e) {
            return null;
        }
    }

    /** The text of a value. */
    public static String write(Object value) {
        return JSON.writeValueAsString(value);
    }
}
