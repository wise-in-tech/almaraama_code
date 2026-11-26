package com.almaraama.files.api;

/**
 * What an uploaded file is for (ADR-038): it decides the contents accepted, the size limit and the processing.
 * Glossary {@code FilePurpose}.
 */
public enum FilePurpose {

    /** A photo of a ticket (H10): JPEG or PNG, at most 8 MB received, re-encoded as JPEG. */
    TICKET_PHOTO(8L * 1024 * 1024, false),

    /** A document of an equipment (G3): PDF, JPEG or PNG, at most 10 MB, images re-encoded. */
    ASSET_DOCUMENT(10L * 1024 * 1024, true);

    private final long maxBytes;
    private final boolean acceptsPdf;

    /**
     * @param maxBytes   largest file received
     * @param acceptsPdf whether a PDF is accepted besides images
     */
    FilePurpose(long maxBytes, boolean acceptsPdf) {
        this.maxBytes = maxBytes;
        this.acceptsPdf = acceptsPdf;
    }

    /** Largest file received for this purpose, before any processing. */
    public long maxBytes() {
        return maxBytes;
    }

    /** Whether a PDF document is accepted for this purpose. */
    public boolean acceptsPdf() {
        return acceptsPdf;
    }
}
