package com.almaraama.customization.application;

import com.almaraama.customization.api.events.TextUpdatedV1;
import com.almaraama.customization.domain.TextEntry;
import com.almaraama.customization.domain.TextOrigin;
import com.almaraama.customization.domain.TextRepository;
import com.almaraama.customization.domain.TextRevision;
import com.almaraama.customization.domain.TextVariables;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dictionary of the interface texts (M6, criterion 20, ADR-029): the public bundle of each language, kept in
 * memory with its {@code ETag} and recomputed after every change; the release's texts loaded at start-up without ever
 * overwriting an administrator's text; the back-office search, edit, history and rollback. The cache is per
 * instance: one instance in P1 (several instances would need an event to refresh it, sprint 5).
 */
@Service
public class TextDictionary {

    private static final Logger LOG = LoggerFactory.getLogger(TextDictionary.class);

    /** The texts of one language with their version (the {@code ETag}). */
    public record Bundle(Language language, String version, Map<String, String> texts) {
    }

    /** One key with its texts in the languages that have one, and the variables every language must keep. */
    public record Row(String key, Set<String> variables, List<TextEntry> values) {
    }

    /** What the start-up load did. */
    public record SeedReport(int added, int updated, int retired) {
    }

    private final TextRepository texts;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Map<Language, Bundle> bundles = new ConcurrentHashMap<>();

    /**
     * @param texts  the dictionary
     * @param events publishes {@code TextUpdatedV1} for the audit log
     * @param clock  time of changes
     */
    TextDictionary(TextRepository texts, ApplicationEventPublisher events, Clock clock) {
        this.texts = texts;
        this.events = events;
        this.clock = clock;
    }

    /** M6, criterion 20: the texts of a language for everyone, without session; computed once, then from memory. */
    @Transactional(readOnly = true)
    public Bundle bundle(Language language) {
        return bundles.computeIfAbsent(language, this::compute);
    }

    /**
     * ADR-029: loads the delivered texts. A new key is added ({@code SEED}); a delivered text that changed replaces a
     * {@code SEED} text, never an {@code EDITED} one; a key gone from a language's file becomes obsolete (kept, M2).
     * Every change of a shown text gets a {@code SYSTEM} revision. Idempotent.
     */
    @Transactional
    public SeedReport seed(Map<Language, Map<String, String>> delivered) {
        Instant now = clock.instant();
        Map<String, TextEntry> existing = new HashMap<>();
        texts.all().forEach(e -> existing.put(e.key() + "|" + e.language(), e));
        int added = 0;
        int updated = 0;
        int retired = 0;
        for (Map.Entry<Language, Map<String, String>> language : delivered.entrySet()) {
            for (Map.Entry<String, String> text : language.getValue().entrySet()) {
                TextEntry entry = existing.remove(text.getKey() + "|" + language.getKey());
                if (entry == null) {
                    TextEntry created = texts.save(TextEntry.seed(text.getKey(), language.getKey(), text.getValue(),
                            now));
                    texts.append(TextRevision.of(created, null, Actor.platform(), now));
                    added++;
                } else {
                    String before = entry.text();
                    boolean shownChanged = entry.deliver(text.getValue(), now);
                    texts.save(entry);
                    if (shownChanged) {
                        texts.append(TextRevision.of(entry, before, Actor.platform(), now));
                        updated++;
                    }
                }
            }
        }
        for (TextEntry gone : existing.values()) {
            if (delivered.containsKey(gone.language()) && gone.retire(now)) {
                texts.save(gone);
                retired++;
            }
        }
        bundles.clear();
        LOG.info("Text dictionary loaded: {} added, {} updated, {} obsolete", added, updated, retired);
        return new SeedReport(added, updated, retired);
    }

    /**
     * Back-office search (ADR-029): keys matching {@code q} in the key or a text, optionally only those missing in a
     * language or edited, by key; needs {@code text:read} on the platform.
     */
    @Transactional(readOnly = true)
    public PageResult<Row> search(Perimeter perimeter, String q, Language missingIn, boolean editedOnly,
                                  PageQuery page) {
        perimeter.requireOnPlatform(Permission.TEXT_READ);
        String filter = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        Map<String, List<TextEntry>> byKey = texts.ofKeys(texts.keys()).stream()
                .collect(Collectors.groupingBy(TextEntry::key, TreeMap::new, Collectors.toList()));
        List<Row> rows = byKey.entrySet().stream()
                .filter(e -> filter.isEmpty() || e.getKey().toLowerCase(Locale.ROOT).contains(filter)
                        || e.getValue().stream().anyMatch(t -> t.text().toLowerCase(Locale.ROOT).contains(filter)))
                .filter(e -> missingIn == null || e.getValue().stream().noneMatch(t -> t.language() == missingIn))
                .filter(e -> !editedOnly || e.getValue().stream().anyMatch(t -> t.origin() == TextOrigin.EDITED))
                .map(e -> new Row(e.getKey(), variables(e.getValue()), e.getValue()))
                .toList();
        int from = (int) Math.min(page.offset(), rows.size());
        int to = Math.min(from + page.size(), rows.size());
        return new PageResult<>(rows.subList(from, to), page.page(), page.size(), rows.size());
    }

    /**
     * M6, criterion 20: an administrator changes one text, visible without deployment (the public bundle changes its
     * version at once). Needs {@code text:update}. {@code NOT_FOUND} for an unknown or obsolete key, {@code
     * VERSION_CONFLICT}, {@code TEXT_INVALID}, {@code TEXT_VARIABLES_CHANGED}. Publishes {@code TextUpdatedV1}.
     *
     * @param version the version read, or {@code null} when the language had no text yet
     */
    @Transactional
    public TextEntry update(Perimeter perimeter, String key, Language language, String text, Long version) {
        perimeter.requireOnPlatform(Permission.TEXT_UPDATE);
        List<TextEntry> ofKey = texts.ofKeys(List.of(key));
        if (ofKey.isEmpty()) {
            throw notFound();
        }
        Instant now = clock.instant();
        TextEntry entry = ofKey.stream().filter(e -> e.language() == language).findFirst()
                .orElseGet(() -> TextEntry.missing(key, language, now));
        if (version != null && entry.version() != version) {
            throw new BusinessException("VERSION_CONFLICT", ErrorCategory.CONFLICT, "Changed in the meantime");
        }
        return change(perimeter, entry, text, variables(ofKey), now);
    }

    /** The history of a key, every language, newest first; needs {@code text:read}; {@code NOT_FOUND}. */
    @Transactional(readOnly = true)
    public PageResult<TextRevision> revisions(Perimeter perimeter, String key, PageQuery page) {
        perimeter.requireOnPlatform(Permission.TEXT_READ);
        if (texts.ofKeys(List.of(key)).isEmpty()) {
            throw notFound();
        }
        return texts.revisions(key, page);
    }

    /**
     * Rollback (M2): puts back the text a revision had, as a new revision. Needs {@code text:update}; {@code
     * NOT_FOUND}, {@code TEXT_VARIABLES_CHANGED} when the delivered text changed its variables since.
     */
    @Transactional
    public TextEntry restore(Perimeter perimeter, UUID revisionId) {
        perimeter.requireOnPlatform(Permission.TEXT_UPDATE);
        TextRevision revision = texts.revision(revisionId).orElseThrow(TextDictionary::notFound);
        List<TextEntry> ofKey = texts.ofKeys(List.of(revision.key()));
        TextEntry entry = ofKey.stream().filter(e -> e.language() == revision.language()).findFirst()
                .orElseThrow(TextDictionary::notFound);
        return change(perimeter, entry, revision.newText(), variables(ofKey), clock.instant());
    }

    /** Applies an administrator's text, records the revision, refreshes the bundle and publishes the event. */
    private TextEntry change(Perimeter perimeter, TextEntry entry, String text, Set<String> variables, Instant now) {
        String before = entry.version() == 0 && entry.text().isEmpty() ? null : entry.text();
        entry.edit(text, variables, perimeter.actor().id(), now);
        TextEntry saved = texts.save(entry);
        texts.append(TextRevision.of(saved, before, perimeter.actor(), now));
        bundles.remove(saved.language());
        events.publishEvent(new TextUpdatedV1(saved.key(), saved.language().name(), saved.origin().name(),
                perimeter.actor(), now));
        LOG.debug("Text {} ({}) changed, now {}", saved.key(), saved.language(), saved.origin());
        return saved;
    }

    /** The variables of a key: those of its delivered texts (the same in every language). */
    private static Set<String> variables(List<TextEntry> entries) {
        return entries.stream().map(TextEntry::seedText).flatMap(Optional::stream).findFirst()
                .map(TextVariables::of).orElseGet(() -> entries.isEmpty() ? Set.of()
                        : TextVariables.of(entries.getFirst().text()));
    }

    /** Reads a language's current texts and computes their version. */
    private Bundle compute(Language language) {
        Map<String, String> map = new TreeMap<>();
        texts.current(language).forEach(e -> map.put(e.key(), e.text()));
        return new Bundle(language, fingerprint(map), Map.copyOf(map));
    }

    /** SHA-256 of the sorted keys and texts, shortened: changes whenever a text changes. */
    private static String fingerprint(Map<String, String> sorted) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            sorted.forEach((k, v) -> {
                digest.update(k.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(v.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 1);
            });
            return HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The single answer for an unknown key or revision. */
    private static BusinessException notFound() {
        return new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "Unknown key");
    }
}
