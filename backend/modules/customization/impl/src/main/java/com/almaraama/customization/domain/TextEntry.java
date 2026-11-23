package com.almaraama.customization.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One text of the interface in one language (M6, ADR-029): a key from the developers, the current text, the delivered
 * text kept to check variables, and its origin. A text an administrator edited is never overwritten by a release.
 * Never deleted: a key gone from the delivered files is marked obsolete (M2).
 */
@Entity
@Table(schema = "customization", name = "text_entry")
public class TextEntry {

    public static final int MAX_LENGTH = 2_000;

    @Id
    private UUID id;

    @Column(name = "text_key", nullable = false)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private Language language;

    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "seed_text")
    private String seedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private TextOrigin origin;

    @Column(name = "obsolete", nullable = false)
    private boolean obsolete;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected TextEntry() {
    }

    /** A delivered text seen for the first time (origin {@code SEED}). */
    public static TextEntry seed(String key, Language language, String text, Instant now) {
        TextEntry entry = new TextEntry();
        entry.id = UuidV7.next();
        entry.key = Objects.requireNonNull(key, "key");
        entry.language = Objects.requireNonNull(language, "language");
        entry.text = text;
        entry.seedText = text;
        entry.origin = TextOrigin.SEED;
        entry.updatedAt = now;
        return entry;
    }

    /**
     * A language with no text yet for an existing key (a missing translation), to be filled by {@link #edit}; never
     * saved before.
     */
    public static TextEntry missing(String key, Language language, Instant now) {
        TextEntry entry = new TextEntry();
        entry.id = UuidV7.next();
        entry.key = Objects.requireNonNull(key, "key");
        entry.language = Objects.requireNonNull(language, "language");
        entry.text = "";
        entry.origin = TextOrigin.EDITED;
        entry.updatedAt = now;
        return entry;
    }

    /**
     * A release delivers this text: a {@code SEED} text follows it, an {@code EDITED} one keeps the administrator's
     * text (only the delivered text is remembered); an obsolete key comes back. Returns whether the shown text changed.
     */
    public boolean deliver(String delivered, Instant now) {
        boolean changed = false;
        if (!delivered.equals(seedText)) {
            seedText = delivered;
            updatedAt = now;
            if (origin == TextOrigin.SEED && !delivered.equals(text)) {
                text = delivered;
                changed = true;
            }
        }
        if (obsolete) {
            obsolete = false;
            updatedAt = now;
            changed = true;
        }
        return changed;
    }

    /** The key is gone from the delivered files: kept, no longer sent (M2). Returns whether it changed. */
    public boolean retire(Instant now) {
        if (obsolete) {
            return false;
        }
        obsolete = true;
        updatedAt = now;
        return true;
    }

    /**
     * An administrator's text (M6, criterion 20): plain text, 1 to 2,000 characters, exactly the {@code {{name}}}
     * variables of the delivered text ({@code expectedVariables}: this language's, or another language's for a
     * missing translation). Becomes {@code EDITED}, unless it equals the delivered text ({@code SEED} again). Errors:
     * {@code TEXT_INVALID}, {@code TEXT_VARIABLES_CHANGED}.
     */
    public void edit(String newText, Set<String> expectedVariables, UUID by, Instant now) {
        String clean = newText == null ? "" : newText.strip();
        if (clean.isEmpty() || clean.length() > MAX_LENGTH) {
            throw new BusinessException("TEXT_INVALID", ErrorCategory.VALIDATION, "1 to 2,000 characters");
        }
        if (!TextVariables.of(clean).equals(expectedVariables)) {
            throw new BusinessException("TEXT_VARIABLES_CHANGED", ErrorCategory.RULE_VIOLATION,
                    "Keep the variables of the delivered text");
        }
        text = clean;
        origin = clean.equals(seedText) ? TextOrigin.SEED : TextOrigin.EDITED;
        updatedBy = by;
        updatedAt = now;
    }

    /** Identifier of the entry. */
    public UUID id() {
        return id;
    }

    /** The key, for example {@code auth.login.title}. */
    public String key() {
        return key;
    }

    /** The language of the text. */
    public Language language() {
        return language;
    }

    /** The text shown. */
    public String text() {
        return text;
    }

    /** The delivered text. */
    public Optional<String> seedText() {
        return Optional.ofNullable(seedText);
    }

    /** Delivered or edited. */
    public TextOrigin origin() {
        return origin;
    }

    /** Whether the key is gone from the delivered files. */
    public boolean obsolete() {
        return obsolete;
    }

    /** The administrator who changed it last, if a person did. */
    public Optional<UUID> updatedBy() {
        return Optional.ofNullable(updatedBy);
    }

    /** When it last changed. */
    public Instant updatedAt() {
        return updatedAt;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
