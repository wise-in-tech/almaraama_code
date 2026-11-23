package com.almaraama.customization.adapters.out.persistence;

import com.almaraama.customization.domain.TextEntry;
import com.almaraama.customization.domain.TextRepository;
import com.almaraama.customization.domain.TextRevision;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** {@link TextRepository} on the {@code customization.text_entry} and {@code text_revision} tables. */
@Component
class JpaTextRepository implements TextRepository {

    private final SpringDataTextEntries entries;
    private final SpringDataTextRevisions revisions;

    /**
     * @param entries   Spring Data repository of the entries
     * @param revisions Spring Data repository of the revisions
     */
    JpaTextRepository(SpringDataTextEntries entries, SpringDataTextRevisions revisions) {
        this.entries = entries;
        this.revisions = revisions;
    }

    /** Every entry. */
    @Override
    public List<TextEntry> all() {
        return entries.findAll();
    }

    /** The current entries of a language. */
    @Override
    public List<TextEntry> current(Language language) {
        return entries.findByLanguageAndObsoleteFalse(language);
    }

    /** One entry. */
    @Override
    public Optional<TextEntry> find(String key, Language language) {
        return entries.findByKeyAndLanguage(key, language);
    }

    /** Every non-obsolete key. */
    @Override
    public List<String> keys() {
        return entries.currentKeys();
    }

    /** The non-obsolete entries of these keys; none for an empty list. */
    @Override
    public List<TextEntry> ofKeys(List<String> keys) {
        return keys.isEmpty() ? List.of() : entries.findByKeyInAndObsoleteFalse(keys);
    }

    /** Inserts or updates an entry. */
    @Override
    public TextEntry save(TextEntry entry) {
        return entries.save(entry);
    }

    /** Inserts a revision. */
    @Override
    public TextRevision append(TextRevision revision) {
        return revisions.save(revision);
    }

    /** One revision. */
    @Override
    public Optional<TextRevision> revision(UUID id) {
        return revisions.findById(id);
    }

    /** The revisions of a key, newest first. */
    @Override
    public PageResult<TextRevision> revisions(String key, PageQuery page) {
        Page<TextRevision> found = revisions.findByKeyOrderByRecordedAtDesc(key,
                PageRequest.of(page.page(), page.size()));
        return new PageResult<>(found.getContent(), page.page(), page.size(), found.getTotalElements());
    }
}
