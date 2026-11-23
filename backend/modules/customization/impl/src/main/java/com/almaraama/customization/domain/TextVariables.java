package com.almaraama.customization.domain;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The {@code {{name}}} variables of a text (ADR-029): an edited text keeps exactly those of its delivered text. */
public final class TextVariables {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

    /** Static helpers only. */
    private TextVariables() {
    }

    /** The variable names of a text, sorted; empty for {@code null}. */
    public static Set<String> of(String text) {
        Set<String> names = new TreeSet<>();
        if (text != null) {
            Matcher matcher = VARIABLE.matcher(text);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }
}
