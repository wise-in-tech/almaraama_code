package com.almaraama.identity.domain;

/**
 * Out port: the most common passwords, which are refused (ASVS V2.1.7, ADR-030). The list is embedded in the
 * application; no external service is called (M4).
 */
public interface CommonPasswordList {

    /** Whether this password, already normalized (NFKC) and lower-cased, is in the list. */
    boolean contains(String normalizedLowerCasePassword);
}
