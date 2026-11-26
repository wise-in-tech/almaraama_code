package com.almaraama.files.application;

import com.almaraama.files.api.FilePurpose;
import com.almaraama.files.api.FilesApi;
import com.almaraama.files.api.SignedLink;
import com.almaraama.files.api.UsableFile;
import com.almaraama.files.domain.CheckedContent;
import com.almaraama.files.domain.ContentKind;
import com.almaraama.files.domain.FileNames;
import com.almaraama.files.domain.FileStorage;
import com.almaraama.files.domain.ImageReencoder;
import com.almaraama.files.domain.StoredFile;
import com.almaraama.files.domain.StoredFileRepository;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.perimeter.Perimeter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upload, attachment and reading of files (ADR-038, H10, G3). The type is detected from the bytes; images are
 * re-encoded as JPEG (metadata and GPS position removed); PDF files are stored as they are and never interpreted.
 */
@Service
public class FileService implements FilesApi {

    private static final Logger LOG = LoggerFactory.getLogger(FileService.class);

    /** Long side of a stored photo, in pixels. */
    static final int MAX_SIDE = 2048;

    /** A file to send back to a reader of a signed link. */
    public record Download(StoredFile file, byte[] bytes) {
    }

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final ImageReencoder images;
    private final LinkSigner signer;
    private final UploadThrottle throttle;
    private final Clock clock;

    /**
     * @param files    the file table
     * @param storage  where the bytes live
     * @param images   re-encoding of photos
     * @param signer   signatures of the links
     * @param throttle uploads per person and per minute
     * @param clock    time of uploads and links
     */
    FileService(StoredFileRepository files, FileStorage storage, ImageReencoder images, LinkSigner signer,
                UploadThrottle throttle, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.images = images;
        this.signer = signer;
        this.throttle = throttle;
        this.clock = clock;
    }

    /**
     * Checks, processes and stores an upload. Needs a session and at least one organization or site in the perimeter
     * ({@code PERMISSION_DENIED}); {@code RATE_LIMITED} beyond 30 per minute; {@code TOO_LARGE} beyond the purpose's
     * limit; {@code UNSUPPORTED_CONTENT} for another content or an unreadable image; {@code IMAGE_TOO_LARGE}.
     */
    @Transactional
    public StoredFile upload(Perimeter perimeter, FilePurpose purpose, byte[] content, String originalName) {
        if (perimeter.organizations().isEmpty() && perimeter.sites().isEmpty()) {
            throw new BusinessException("PERMISSION_DENIED", ErrorCategory.PERMISSION_DENIED, "No site");
        }
        Instant now = clock.instant();
        throttle.acquire(perimeter.actor().id(), now);
        if (content.length == 0) {
            throw new BusinessException("UNSUPPORTED_CONTENT", ErrorCategory.UNSUPPORTED_CONTENT, "Empty file");
        }
        if (content.length > purpose.maxBytes()) {
            throw new BusinessException("TOO_LARGE", ErrorCategory.TOO_LARGE, "File over the limit");
        }
        ContentKind kind = ContentKind.detect(content)
                .filter(k -> k.image() || purpose.acceptsPdf())
                .orElseThrow(() -> new BusinessException("UNSUPPORTED_CONTENT", ErrorCategory.UNSUPPORTED_CONTENT,
                        "Content not accepted for " + purpose));
        byte[] stored = kind.image() ? images.toJpeg(content, MAX_SIDE) : content;
        CheckedContent checked = new CheckedContent(stored, kind.image() ? "image/jpeg" : "application/pdf",
                sha256(stored));
        String key = UUID.randomUUID().toString();
        storage.put(key, stored);
        StoredFile file = files.save(StoredFile.uploaded(purpose, checked, key, FileNames.clean(originalName),
                perimeter.actor(), now));
        LOG.debug("File {} stored for {} ({} kind, {} bytes received, {} stored)", file.id(), purpose, kind,
                content.length, stored.length);
        return file;
    }

    /**
     * The file behind a signed link, or {@code NOT_FOUND} for a wrong or expired signature, an unknown file or a file
     * never attached (ADR-038).
     */
    @Transactional(readOnly = true)
    public Download read(UUID fileId, long expires, String signature) {
        if (!signer.valid(fileId, expires, signature, clock.instant())) {
            throw notFound();
        }
        StoredFile file = files.findById(fileId).filter(StoredFile::attached).orElseThrow(FileService::notFound);
        byte[] bytes = storage.get(file.storageKey()).orElseThrow(() -> {
            LOG.error("File {} missing from the storage", fileId);
            return notFound();
        });
        return new Download(file, bytes);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public UsableFile requireUsable(UUID fileId, Actor actor, FilePurpose purpose) {
        StoredFile file = usable(fileId, actor, purpose);
        return new UsableFile(file.id(), file.purpose(), file.contentType());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public UsableFile attach(UUID fileId, Actor actor, FilePurpose purpose, String ownerModule, UUID ownerId) {
        StoredFile file = usable(fileId, actor, purpose);
        file.attachTo(ownerModule, ownerId, clock.instant());
        files.save(file);
        LOG.debug("File {} attached to {} {}", fileId, ownerModule, ownerId);
        return new UsableFile(file.id(), file.purpose(), file.contentType());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public SignedLink signedLink(UUID fileId) {
        return files.findById(fileId).map(this::link).orElseThrow(FileService::notFound);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SignedLink> signedLinks(Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return files.findAllById(fileIds).stream().map(this::link)
                .collect(Collectors.toMap(SignedLink::fileId, Function.identity()));
    }

    /** The file when this actor may attach it, else {@code FILE_NOT_USABLE}. */
    private StoredFile usable(UUID fileId, Actor actor, FilePurpose purpose) {
        Optional<StoredFile> file = files.findById(fileId);
        if (file.isEmpty() || !file.get().usableBy(actor, purpose, clock.instant())) {
            throw new BusinessException("FILE_NOT_USABLE", ErrorCategory.RULE_VIOLATION,
                    "File of someone else, used, too old or of another purpose");
        }
        return file.get();
    }

    /** A link valid 5 minutes. */
    private SignedLink link(StoredFile file) {
        Instant expires = signer.expiryFrom(clock.instant());
        long epoch = expires.getEpochSecond();
        String url = "/api/v1/files/" + file.id() + "/content?expires=" + epoch + "&signature="
                + signer.sign(file.id(), epoch);
        return new SignedLink(file.id(), url, file.contentType(), expires);
    }

    /** Hexadecimal SHA-256. */
    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The answer for a missing file or a bad link. */
    private static BusinessException notFound() {
        return new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "No such file");
    }
}
