package com.almaraama.customization.adapters.out.texts;

import com.almaraama.customization.domain.DeliveredTexts;
import com.almaraama.shared.i18n.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link DeliveredTexts} from {@code texts/<language>.json} in the jar (ADR-029): a copy of the frontend's locale files
 * made by {@code pnpm sync:texts}, checked identical by {@code pnpm verify}. Nested keys are flattened with dots.
 */
@Component
class ClasspathDeliveredTexts implements DeliveredTexts {

    private final JsonMapper json = JsonMapper.builder().build();

    /** Reads the three files; a missing file gives no text for that language. */
    @Override
    public Map<Language, Map<String, String>> load() {
        Map<Language, Map<String, String>> all = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            String resource = "/texts/" + language.name().toLowerCase(Locale.ROOT) + ".json";
            try (InputStream in = ClasspathDeliveredTexts.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                Map<String, String> flat = new TreeMap<>();
                flatten("", json.readValue(in, Map.class), flat);
                all.put(language, flat);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return all;
    }

    /** Writes every leaf of a nested map under its dotted key; arrays and numbers are written as text. */
    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> node, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> child) {
                flatten(key, (Map<String, Object>) child, out);
            } else if (entry.getValue() != null) {
                out.put(key, String.valueOf(entry.getValue()));
            }
        }
    }
}
