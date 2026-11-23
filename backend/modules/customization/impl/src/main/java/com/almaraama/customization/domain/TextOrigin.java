package com.almaraama.customization.domain;

/** Where the current text of an entry comes from (M6, ADR-029). Glossary {@code TextOrigin}. */
public enum TextOrigin {
    /** Delivered by the team with a release (locale files). */
    SEED,
    /** Changed by a platform administrator: a release never overwrites it. */
    EDITED
}
