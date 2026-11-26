package com.almaraama.files.api;

import java.time.Instant;
import java.util.UUID;

/**
 * A short-lived link to read a stored file (ADR-038): built by the owning module after its own visibility check,
 * valid 5 minutes, protected by an HMAC signature. Never logged.
 *
 * @param fileId      the file
 * @param url         relative URL under the API context path ({@code /api/v1/files/{id}/content?...})
 * @param contentType detected content type
 * @param expiresAt   end of validity
 */
public record SignedLink(UUID fileId, String url, String contentType, Instant expiresAt) {
}
