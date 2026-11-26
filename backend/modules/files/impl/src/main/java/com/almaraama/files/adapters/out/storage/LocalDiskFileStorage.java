package com.almaraama.files.adapters.out.storage;

import com.almaraama.files.domain.FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link FileStorage} on a local directory outside the image and the web root (ADR-038), on the encrypted disk of the
 * data server ({@code ALMARAAMA_FILES_DIR}, a Docker volume). Keys are random UUIDs spread over two levels of
 * sub-directories; anything else is refused, so a key can never point outside the directory. Writes go to a temporary
 * file renamed atomically.
 */
@Component
class LocalDiskFileStorage implements FileStorage {

    private static final Pattern KEY = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final Path root;

    /** @param directory root directory of the files; created when missing */
    LocalDiskFileStorage(@Value("${almaraama.files.directory:${java.io.tmpdir}/almaraama-files}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Files directory unusable: " + root, e);
        }
    }

    /** Writes the bytes under the key, through a temporary file and an atomic rename. */
    @Override
    public void put(String key, byte[] bytes) {
        Path target = path(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("File not written", e);
        }
    }

    /** The bytes under the key, if the file exists. */
    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(Files.readAllBytes(path(key)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("File not read", e);
        }
    }

    /** {@code root/ab/cd/<key>} for a UUID key; anything else is a programming error. */
    private Path path(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Storage key must be a UUID");
        }
        return root.resolve(key.substring(0, 2)).resolve(key.substring(2, 4)).resolve(key);
    }
}
