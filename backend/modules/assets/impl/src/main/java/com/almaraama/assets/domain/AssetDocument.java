package com.almaraama.assets.domain;

import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A document of an equipment (G3): a stored file of the {@code files} module. Archived, never deleted (M2). */
@Entity
@Table(schema = "assets", name = "asset_document")
public class AssetDocument {

    @Id
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AssetDocumentType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected AssetDocument() {
    }

    /** A document attached by a person. {@code ASSET_DOCUMENT_INVALID} for a missing or too long title. */
    public static AssetDocument attach(UUID assetId, UUID fileId, AssetDocumentType type, String title, UUID addedBy,
                                       Instant now) {
        AssetDocument document = new AssetDocument();
        document.id = UuidV7.next();
        document.assetId = assetId;
        document.fileId = fileId;
        document.type = type;
        document.title = Labels.required(title, 120, "ASSET_DOCUMENT_INVALID");
        document.addedBy = addedBy;
        document.addedAt = now;
        return document;
    }

    /** Hides the document; the file is kept (M2). */
    public void archive(Instant now) {
        if (archivedAt == null) {
            archivedAt = now;
        }
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The equipment. */
    public UUID assetId() {
        return assetId;
    }

    /** The stored file. */
    public UUID fileId() {
        return fileId;
    }

    /** Kind of document. */
    public AssetDocumentType type() {
        return type;
    }

    /** Title. */
    public String title() {
        return title;
    }

    /** When it was attached. */
    public Instant addedAt() {
        return addedAt;
    }

    /** Whether archived. */
    public boolean archived() {
        return archivedAt != null;
    }
}
