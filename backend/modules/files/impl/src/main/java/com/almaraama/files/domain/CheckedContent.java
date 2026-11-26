package com.almaraama.files.domain;

/**
 * Bytes whose type was detected from their content and, for an image, re-encoded (ADR-038).
 *
 * @param bytes       what is stored
 * @param contentType detected type of the stored bytes
 * @param sha256      hexadecimal SHA-256 of the stored bytes
 */
public record CheckedContent(byte[] bytes, String contentType, String sha256) {
}
