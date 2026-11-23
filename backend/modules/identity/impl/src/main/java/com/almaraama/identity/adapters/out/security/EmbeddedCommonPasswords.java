package com.almaraama.identity.adapters.out.security;

import com.almaraama.identity.domain.CommonPasswordList;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The 10,000 most common passwords of 8 characters or more, embedded in the jar ({@code
 * identity/common-passwords.txt}): taken from SecLists, xato-net-10-million-passwords-100000 (MIT licence),
 * normalized (NFKC) and lower-cased, in frequency order (ADR-030, ASVS V2.1.7). Loaded once at start-up.
 */
@Component
class EmbeddedCommonPasswords implements CommonPasswordList {

    private static final String RESOURCE = "/identity/common-passwords.txt";

    private final Set<String> passwords;

    /** Reads the embedded list; fails at start-up if it is missing, so the rule can never be silently off. */
    EmbeddedCommonPasswords() {
        Set<String> loaded = new HashSet<>(16_384);
        try (InputStream in = EmbeddedCommonPasswords.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isEmpty()) {
                    loaded.add(line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.passwords = Set.copyOf(loaded);
    }

    /** Whether the normalized, lower-cased password is in the list. */
    @Override
    public boolean contains(String normalizedLowerCasePassword) {
        return passwords.contains(normalizedLowerCasePassword);
    }
}
