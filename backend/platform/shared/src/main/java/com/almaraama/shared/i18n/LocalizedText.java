package com.almaraama.shared.i18n;

import java.util.Optional;

/**
 * A text shown to users in the three languages of the interface (M1): Arabic, English, French. Used for the labels of
 * reference data (equipment families, issue types, circuit steps…) that every module stores in its own columns. Any
 * language may be missing in storage; {@link #filled()} applies the fallback of M6.
 *
 * @param ar Arabic text, or {@code null}
 * @param en English text, or {@code null}
 * @param fr French text, or {@code null}
 */
public record LocalizedText(String ar, String en, String fr) {

    /** Blank texts become {@code null}; other texts are trimmed. */
    public LocalizedText {
        ar = clean(ar);
        en = clean(en);
        fr = clean(fr);
    }

    /** The same text in every language, for codes without translation. */
    public static LocalizedText same(String text) {
        return new LocalizedText(text, text, text);
    }

    /** Whether at least one language is present. */
    public boolean isPresent() {
        return ar != null || en != null || fr != null;
    }

    /** Whether one language is longer than {@code max} characters. */
    public boolean longerThan(int max) {
        return (ar != null && ar.length() > max) || (en != null && en.length() > max)
                || (fr != null && fr.length() > max);
    }

    /**
     * Every language filled with the fallback of M6: a missing language takes French, else English, else Arabic, else
     * {@code fallback}.
     */
    public LocalizedText filled(String fallback) {
        String any = fr != null ? fr : en != null ? en : ar != null ? ar : fallback;
        return new LocalizedText(ar != null ? ar : any, en != null ? en : any, fr != null ? fr : any);
    }

    /** The text in one language, if present. */
    public Optional<String> in(Language language) {
        return Optional.ofNullable(switch (language) {
            case AR -> ar;
            case EN -> en;
            case FR -> fr;
        });
    }

    /** Trims a text; blank gives {@code null}. */
    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
