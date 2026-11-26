package com.almaraama.assets.domain;

import com.almaraama.shared.i18n.Language;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Out port: the Excel file of an equipment import (ADR-027 revision 3). The adapter checks the file (type by content,
 * size, rows, traps) and returns its lines; the use case never sees the file format.
 */
public interface AssetSheet {

    /** Column keys of the template, in order. */
    List<String> COLUMNS = List.of("name", "family", "model", "reference", "building", "floor", "outdoorArea",
            "serialNumber", "state", "commissionedOn", "supplier");

    /** Columns that must exist in the hidden key row. */
    List<String> REQUIRED_COLUMNS = List.of("name", "family");

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

    /** What the template offers as lists. */
    record TemplateLists(List<String> familyCodes, List<String> buildings, List<String> outdoorAreas,
                         List<String> suppliers) {
    }

    /**
     * The non-empty lines of the file. {@code IMPORT_FILE_INVALID} (not an {@code .xlsx} built from the template,
     * trapped file), {@code IMPORT_FILE_TOO_LARGE} (over 2 MB or 2,000 rows).
     */
    List<Line> read(byte[] content);

    /** The template in this language with these lists. */
    byte[] template(Language language, TemplateLists lists);
}
