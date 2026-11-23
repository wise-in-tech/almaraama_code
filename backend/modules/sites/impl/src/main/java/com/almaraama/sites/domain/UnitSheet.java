package com.almaraama.sites.domain;

import com.almaraama.shared.i18n.Language;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Out port: the Excel file of a unit import (A6, criterion 1, ADR-027). The adapter checks the file (type by content,
 * size, rows, traps) and returns its lines; the use case never sees the file format.
 */
public interface UnitSheet {

    /** Column keys of the template, in order. */
    List<String> COLUMNS = List.of("reference", "type", "category", "building", "outdoorArea", "floor", "annexOf",
            "saleable", "rentable");

    /** Columns that must exist in the hidden key row. */
    List<String> REQUIRED_COLUMNS = List.of("reference", "type", "category");

    /**
     * One data line of the file.
     *
     * @param excelRow row number shown by Excel
     * @param values   trimmed, non-empty cells by column key
     */
    record Line(int excelRow, Map<String, String> values) {

        /** The cell of this column, empty when blank. */
        public Optional<String> get(String key) {
            return Optional.ofNullable(values.get(key));
        }
    }

    /**
     * The non-empty lines of the file. {@code IMPORT_FILE_INVALID} (not an {@code .xlsx} built from the template,
     * trapped file), {@code IMPORT_FILE_TOO_LARGE} (over 2 MB or 2,000 rows).
     */
    List<Line> read(byte[] content);

    /** The template in this language, offering these category codes in the {@code category} column. */
    byte[] template(Language language, List<String> categoryCodes);
}
