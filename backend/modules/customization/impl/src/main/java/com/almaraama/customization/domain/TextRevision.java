package com.almaraama.customization.domain;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One change of a text (M6, M2): old and new text, by a release ({@code SYSTEM}) or an administrator. Append only: the
 * application may insert, never update (database rights). A rollback is a new revision.
 */
@Entity
@Table(schema = "customization", name = "text_revision")
public class TextRevision {

    @Id
    private UUID id;

    @Column(name = "entry_id", nullable = false, updatable = false)
    private UUID entryId;

    @Column(name = "text_key", nullable = false, updatable = false)
    private String key;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, updatable = false)
    private Language language;

    @Column(name = "old_text", updatable = false)
    private String oldText;

    @Column(name = "new_text", nullable = false, updatable = false)
    private String newText;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false)
    private TextOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false)
    private ActorType actorType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    /** For JPA only. */
    protected TextRevision() {
    }

    /** The revision of an entry that just changed from {@code oldText} (null for a new entry). */
    public static TextRevision of(TextEntry entry, String oldText, Actor actor, Instant now) {
        TextRevision revision = new TextRevision();
        revision.id = UuidV7.next();
        revision.entryId = entry.id();
        revision.key = entry.key();
        revision.language = entry.language();
        revision.oldText = oldText;
        revision.newText = entry.text();
        revision.origin = entry.origin();
        revision.actorType = actor.type();
        revision.actorId = actor.id();
        revision.recordedAt = now;
        return revision;
    }

    /** Identifier of the revision. */
    public UUID id() {
        return id;
    }

    /** The entry it belongs to. */
    public UUID entryId() {
        return entryId;
    }

    /** The key. */
    public String key() {
        return key;
    }

    /** The language. */
    public Language language() {
        return language;
    }

    /** The text before, absent for a new entry. */
    public Optional<String> oldText() {
        return Optional.ofNullable(oldText);
    }

    /** The text after. */
    public String newText() {
        return newText;
    }

    /** Origin of the text after. */
    public TextOrigin origin() {
        return origin;
    }

    /** {@code SYSTEM} for a release, {@code PERSON} for an administrator. */
    public ActorType actorType() {
        return actorType;
    }

    /** Who changed it. */
    public UUID actorId() {
        return actorId;
    }

    /** When. */
    public Instant recordedAt() {
        return recordedAt;
    }
}
