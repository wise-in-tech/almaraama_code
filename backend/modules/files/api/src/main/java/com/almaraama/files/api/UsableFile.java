package com.almaraama.files.api;

import java.util.UUID;

/**
 * A freshly uploaded file that its uploader may attach (ADR-038).
 *
 * @param fileId      the file
 * @param purpose     what it was uploaded for
 * @param contentType detected content type, after re-encoding
 */
public record UsableFile(UUID fileId, FilePurpose purpose, String contentType) {
}
