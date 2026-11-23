package com.almaraama.customization.adapters.out.persistence;

import com.almaraama.customization.domain.TextEntry;
import com.almaraama.shared.i18n.Language;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Entries of the dictionary, schema customization. No delete (M2). */
interface SpringDataTextEntries extends Repository<TextEntry, UUID> {

    /** Every entry. */
    List<TextEntry> findAll();

    /** The current entries of a language. */
    List<TextEntry> findByLanguageAndObsoleteFalse(Language language);

    /** One entry. */
    Optional<TextEntry> findByKeyAndLanguage(String key, Language language);

    /** Every non-obsolete key, sorted. */
    @Query("select distinct e.key from TextEntry e where e.obsolete = false order by e.key")
    List<String> currentKeys();

    /** The non-obsolete entries of these keys. */
    List<TextEntry> findByKeyInAndObsoleteFalse(Collection<String> keys);

    /** Inserts or updates an entry. */
    TextEntry save(TextEntry entry);
}
