package com.almaraama.files.adapters.out.persistence;

import com.almaraama.files.domain.StoredFile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code files.stored_file}; no delete (M2). */
interface SpringDataStoredFiles extends Repository<StoredFile, UUID> {

    /** Loads a file. */
    Optional<StoredFile> findById(UUID id);

    /** Loads several files. */
    List<StoredFile> findByIdIn(Collection<UUID> ids);

    /** Inserts or updates a file. */
    StoredFile save(StoredFile file);
}
