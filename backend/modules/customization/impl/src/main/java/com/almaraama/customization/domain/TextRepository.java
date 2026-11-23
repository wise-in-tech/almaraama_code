package com.almaraama.customization.domain;

import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: the text dictionary and its revisions (M6, ADR-029). Nothing is deleted (M2). */
public interface TextRepository {

    /** Every entry, obsolete ones included. */
    List<TextEntry> all();

    /** The current entries of one language, obsolete ones left out. */
    List<TextEntry> current(Language language);

    /** One entry. */
    Optional<TextEntry> find(String key, Language language);

    /** Every non-obsolete key, sorted, for the back-office. */
    List<String> keys();

    /** The non-obsolete entries of these keys, every language. */
    List<TextEntry> ofKeys(List<String> keys);

    /** Inserts or updates an entry. */
    TextEntry save(TextEntry entry);

    /** Appends a revision. */
    TextRevision append(TextRevision revision);

    /** One revision. */
    Optional<TextRevision> revision(UUID id);

    /** The revisions of a key, newest first. */
    PageResult<TextRevision> revisions(String key, PageQuery page);
}
