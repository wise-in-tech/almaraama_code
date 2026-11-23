package com.almaraama.spreadsheet;

/**
 * A file that cannot be imported (ADR-027). The adapter turns it into the contract's error: {@link Kind#INVALID} into
 * {@code 415 IMPORT_FILE_INVALID}, {@link Kind#TOO_LARGE} into {@code 413 IMPORT_FILE_TOO_LARGE}. The message never
 * contains a cell value.
 */
public class SpreadsheetException extends RuntimeException {

    /** Why the file was refused. */
    public enum Kind {
        /** Not an {@code .xlsx} workbook, a workbook with macros, a trapped file, or not built from the template. */
        INVALID,
        /** More bytes or more rows than allowed. */
        TOO_LARGE
    }

    private final Kind kind;

    /**
     * @param kind    why the file was refused
     * @param message technical reason, for the logs only (never shown to the person, never a cell value)
     */
    public SpreadsheetException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    /**
     * @param kind    why the file was refused
     * @param message technical reason, for the logs only
     * @param cause   the library's error
     */
    public SpreadsheetException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /** Why the file was refused. */
    public Kind kind() {
        return kind;
    }
}
