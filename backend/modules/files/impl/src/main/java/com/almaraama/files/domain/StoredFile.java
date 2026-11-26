package com.almaraama.files.domain;

import com.almaraama.files.api.FilePurpose;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A stored file (ADR-038): what was checked about it and who may attach it. The bytes live in the
 * {@link FileStorage} under {@link #storageKey()}, a random key that never contains a name typed by a user. Never
 * deleted (M2).
 */
@Entity
@Table(schema = "files", name = "stored_file")
public class StoredFile {

    /** How long an uploaded file waits to be attached by its uploader. */
    public static final Duration USABLE_FOR = Duration.ofHours(24);

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private FilePurpose purpose;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "original_name")
    private String originalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploaded_by_type", nullable = false)
    private ActorType uploadedByType;

    @Column(name = "uploaded_by_id", nullable = false)
    private UUID uploadedById;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "owner_module")
    private String ownerModule;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "attached_at")
    private Instant attachedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected StoredFile() {
    }

    /**
     * A file just checked and written to the storage.
     *
     * @param content      the checked (and for images re-encoded) content
     * @param originalName name given by the browser, cleaned by {@link FileNames#clean}; display only
     */
    public static StoredFile uploaded(FilePurpose purpose, CheckedContent content, String storageKey,
                                      String originalName, Actor uploader, Instant now) {
        StoredFile file = new StoredFile();
        file.id = UuidV7.next();
        file.purpose = purpose;
        file.contentType = content.contentType();
        file.sizeBytes = content.bytes().length;
        file.sha256 = content.sha256();
        file.storageKey = storageKey;
        file.originalName = originalName;
        file.uploadedByType = uploader.type();
        file.uploadedById = uploader.id();
        file.uploadedAt = now;
        return file;
    }

    /**
     * Whether this actor may attach the file now: they uploaded it, for this purpose, less than 24 hours ago, and it was
     * never attached (ADR-038).
     */
    public boolean usableBy(Actor actor, FilePurpose expected, Instant now) {
        return attachedAt == null && purpose == expected && uploadedByType == actor.type()
                && uploadedById.equals(actor.id()) && now.isBefore(uploadedAt.plus(USABLE_FOR));
    }

    /** Records the owner of the file; called once, after {@link #usableBy}. */
    public void attachTo(String module, UUID owner, Instant now) {
        this.ownerModule = module;
        this.ownerId = owner;
        this.attachedAt = now;
    }

    /** Identifier of the file. */
    public UUID id() {
        return id;
    }

    /** What the file was uploaded for. */
    public FilePurpose purpose() {
        return purpose;
    }

    /** Content type detected from the bytes ({@code image/jpeg} or {@code application/pdf}). */
    public String contentType() {
        return contentType;
    }

    /** Stored size in bytes. */
    public long sizeBytes() {
        return sizeBytes;
    }

    /** Random key of the bytes in the storage. */
    public String storageKey() {
        return storageKey;
    }

    /** Cleaned name given at upload, for display and the download name. */
    public Optional<String> originalName() {
        return Optional.ofNullable(originalName);
    }

    /** When it was uploaded. */
    public Instant uploadedAt() {
        return uploadedAt;
    }

    /** Whether an owner has attached the file. */
    public boolean attached() {
        return attachedAt != null;
    }
}
