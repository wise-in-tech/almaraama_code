package com.almaraama.customization.adapters.out.persistence;

import com.almaraama.customization.domain.TextRevision;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Revisions of the dictionary, schema customization. Insert only (M2, database rights). */
interface SpringDataTextRevisions extends Repository<TextRevision, UUID> {

    /** One revision. */
    Optional<TextRevision> findById(UUID id);

    /** The revisions of a key, newest first. */
    Page<TextRevision> findByKeyOrderByRecordedAtDesc(String key, Pageable page);

    /** Inserts a revision. */
    TextRevision save(TextRevision revision);
}
