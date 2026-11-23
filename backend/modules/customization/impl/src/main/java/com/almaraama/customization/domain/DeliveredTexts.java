package com.almaraama.customization.domain;

import com.almaraama.shared.i18n.Language;
import java.util.Map;

/**
 * Out port: the texts delivered with the release (ADR-029), copied from the frontend's locale files by {@code pnpm
 * sync:texts}. Keys are flattened with dots ({@code auth.login.title}).
 */
public interface DeliveredTexts {

    /** Every delivered text, by language then key. */
    Map<Language, Map<String, String>> load();
}
