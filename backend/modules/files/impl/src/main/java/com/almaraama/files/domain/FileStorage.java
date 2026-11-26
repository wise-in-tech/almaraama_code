package com.almaraama.files.domain;

import java.util.Optional;

/**
 * Out port: where the bytes of files live (ADR-010, ADR-038). P1: a directory on the encrypted disk of the data server
 * in Algeria (M4); an S3-compatible store can replace it without touching the rest.
 */
public interface FileStorage {

    /** Writes new bytes under a fresh key; the key never contains user input. */
    void put(String key, byte[] bytes);

    /** The bytes under this key, if present. */
    Optional<byte[]> get(String key);
}
