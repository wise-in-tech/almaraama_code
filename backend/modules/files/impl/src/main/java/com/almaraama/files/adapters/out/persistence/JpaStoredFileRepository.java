package com.almaraama.files.adapters.out.persistence;

import com.almaraama.files.domain.StoredFile;
import com.almaraama.files.domain.StoredFileRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link StoredFileRepository} on the {@code files.stored_file} table. */
@Component
class JpaStoredFileRepository implements StoredFileRepository {

    private final SpringDataStoredFiles files;

    /** @param files Spring Data repository */
    JpaStoredFileRepository(SpringDataStoredFiles files) {
        this.files = files;
    }

    /** Loads a file. */
    @Override
    public Optional<StoredFile> findById(UUID id) {
        return files.findById(id);
    }

    /** Loads several files; an empty input gives an empty list without querying. */
    @Override
    public List<StoredFile> findAllById(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : files.findByIdIn(ids);
    }

    /** Stores a file. */
    @Override
    public StoredFile save(StoredFile file) {
        return files.save(file);
    }
}
