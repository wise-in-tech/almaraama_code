package com.almaraama.workflow.application;

import com.almaraama.shared.i18n.LocalizedText;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Options of a client (H5, ADR-036), read at each use. Glossary {@code WorkflowSettings}.
 *
 * @param autoCloseAfterDays days in a step with {@code autoClose} before the system closes; empty = never (default)
 * @param requesterValidation the requester confirms before the manager closes (off by default)
 * @param labels             the client's labels of the steps, by step code
 */
public record WorkflowSettings(Optional<Integer> autoCloseAfterDays, boolean requesterValidation,
                               Map<String, LocalizedText> labels) {

    /** No option: the defaults of H5. */
    public static WorkflowSettings none() {
        return new WorkflowSettings(Optional.empty(), false, Map.of());
    }

    /** From the JSON text of a binding; unknown keys are ignored. */
    @SuppressWarnings("unchecked")
    public static WorkflowSettings parse(String json) {
        Map<String, Object> raw = Json.object(json);
        Optional<Integer> days = raw.get("autoCloseAfterDays") instanceof Number n ? Optional.of(n.intValue())
                : Optional.empty();
        boolean validation = Boolean.TRUE.equals(raw.get("requesterValidation"));
        Map<String, LocalizedText> labels = new LinkedHashMap<>();
        if (raw.get("labels") instanceof Map<?, ?> map) {
            map.forEach((code, value) -> {
                if (value instanceof Map<?, ?> text) {
                    Map<String, Object> t = (Map<String, Object>) text;
                    labels.put(String.valueOf(code), new LocalizedText((String) t.get("ar"), (String) t.get("en"),
                            (String) t.get("fr")));
                }
            });
        }
        return new WorkflowSettings(days, validation, labels);
    }

    /** The JSON text stored on the binding. */
    public String toJson() {
        Map<String, Object> raw = new LinkedHashMap<>();
        autoCloseAfterDays.ifPresent(d -> raw.put("autoCloseAfterDays", d));
        raw.put("requesterValidation", requesterValidation);
        Map<String, Object> texts = new LinkedHashMap<>();
        labels.forEach((code, text) -> {
            Map<String, String> t = new LinkedHashMap<>();
            if (text.ar() != null) {
                t.put("ar", text.ar());
            }
            if (text.en() != null) {
                t.put("en", text.en());
            }
            if (text.fr() != null) {
                t.put("fr", text.fr());
            }
            texts.put(code, t);
        });
        raw.put("labels", texts);
        return Json.write(raw);
    }

    /** Whether an option named by a condition ({@code OptionEnabled}) is on. */
    public boolean enabled(String option) {
        return switch (option) {
            case "requesterValidation" -> requesterValidation;
            case "autoClose" -> autoCloseAfterDays.isPresent();
            default -> false;
        };
    }
}
