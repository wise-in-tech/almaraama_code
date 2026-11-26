package com.almaraama.customization.api;

/** Type of a characteristic's value (G3, ADR-039). Glossary {@code DataType}. */
public enum AttributeDataType {
    /** Free text, 500 characters at most. */
    TEXT,
    /** Whole number. */
    INTEGER,
    /** Decimal number. */
    DECIMAL,
    /** Calendar date, {@code YYYY-MM-DD}. */
    DATE,
    /** Yes or no. */
    BOOLEAN,
    /** One code among the definition's options. */
    LIST
}
