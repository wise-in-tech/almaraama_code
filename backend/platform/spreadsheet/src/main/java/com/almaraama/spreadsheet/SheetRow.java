package com.almaraama.spreadsheet;

import java.util.Map;
import java.util.Optional;

/**
 * One data row of an imported sheet (ADR-027): its number as Excel shows it, and its non-empty cells by column key.
 *
 * @param excelRow the row number shown by Excel (1-based), for the error report
 * @param values   trimmed, non-empty cell texts by column key ({@code reference}, {@code email}…)
 */
public record SheetRow(int excelRow, Map<String, String> values) {

    public SheetRow {
        values = Map.copyOf(values);
    }

    /** The trimmed text of a column, empty when the cell is empty. */
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
