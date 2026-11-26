package com.almaraama.assets.domain;

/** Kind of a document attached to an equipment (G3). Glossary {@code AssetDocumentType}. */
public enum AssetDocumentType {
    /** User or maintenance manual. */
    MANUAL,
    /** Certificate or inspection report. */
    CERTIFICATE,
    /** Purchase invoice. */
    INVOICE,
    /** Photo of the equipment or its plate. */
    PHOTO,
    /** Anything else. */
    OTHER
}
