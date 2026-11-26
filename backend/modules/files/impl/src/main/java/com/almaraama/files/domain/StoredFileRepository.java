package com.almaraama.files.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: the stored files (ADR-038). Never deleted (M2). */
public interface StoredFileRepository {

    /** Loads a file. */
    Optional<StoredFile> findById(UUID id);

    /** Loads several files; unknown identifiers are left out. */
    List<StoredFile> findAllById(Collection<UUID> ids);

    /** Stores a file. */
    StoredFile save(StoredFile file);
}
