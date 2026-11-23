package com.almaraama.customization.adapters.in.web;

import com.almaraama.customization.adapters.in.web.TextWebDtos.Page;
import com.almaraama.customization.adapters.in.web.TextWebDtos.PageMeta;
import com.almaraama.customization.adapters.in.web.TextWebDtos.PersonRef;
import com.almaraama.customization.adapters.in.web.TextWebDtos.TextBundle;
import com.almaraama.customization.adapters.in.web.TextWebDtos.TextRevisionResponse;
import com.almaraama.customization.adapters.in.web.TextWebDtos.TextRow;
import com.almaraama.customization.adapters.in.web.TextWebDtos.TextValue;
import com.almaraama.customization.adapters.in.web.TextWebDtos.UpdateTextRequest;
import com.almaraama.customization.application.TextDictionary;
import com.almaraama.customization.domain.TextEntry;
import com.almaraama.customization.domain.TextRevision;
import com.almaraama.identity.api.IdentityApi;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * The text dictionary (M6, criterion 20, ADR-029): the public bundle of a language with its {@code ETag}, and the
 * back-office of the platform administrators (search, edit, history, rollback).
 */
@RestController
class TextController {

    private final TextDictionary dictionary;
    private final IdentityApi identity;

    /**
     * @param dictionary the dictionary use cases
     * @param identity   names of the administrators in the history
     */
    TextController(TextDictionary dictionary, IdentityApi identity) {
        this.dictionary = dictionary;
        this.identity = identity;
    }

    /**
     * {@code GET /v1/texts/{language}} (public): the texts of a language; {@code 304} when {@code If-None-Match}
     * carries the current version. {@code Cache-Control: no-cache}: the browser always revalidates.
     */
    @GetMapping("/v1/texts/{language}")
    ResponseEntity<TextBundle> bundle(@PathVariable String language, WebRequest request) {
        TextDictionary.Bundle bundle = dictionary.bundle(language(language));
        String etag = "\"" + bundle.version() + "\"";
        if (request.checkNotModified(etag)) {
            return null;
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache())
                .body(new TextBundle(bundle.language(), bundle.version(), bundle.texts()));
    }

    /** {@code GET /v1/admin/texts}: needs {@code text:read} on the platform. */
    @GetMapping("/v1/admin/texts")
    Page<TextRow> search(Perimeter perimeter, @RequestParam(required = false) String q,
                         @RequestParam(required = false) Language missingIn,
                         @RequestParam(defaultValue = "false") boolean editedOnly,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        String search = q == null ? null : q.strip().substring(0, Math.min(100, q.strip().length()));
        PageResult<TextDictionary.Row> found = dictionary.search(perimeter, search, missingIn, editedOnly,
                PageQuery.of(page, size));
        Map<UUID, PersonRef> people = people(found.items().stream().flatMap(r -> r.values().stream())
                .flatMap(e -> e.updatedBy().stream()).collect(Collectors.toSet()));
        return page(found, r -> new TextRow(r.key(), List.copyOf(r.variables()),
                r.values().stream().map(e -> TextValue.of(e, people)).toList()));
    }

    /** {@code PUT /v1/admin/texts/{key}/{language}}: needs {@code text:update}; visible at once (criterion 20). */
    @PutMapping("/v1/admin/texts/{key}/{language}")
    TextValue update(Perimeter perimeter, @PathVariable String key, @PathVariable String language,
                     @Valid @RequestBody UpdateTextRequest request) {
        TextEntry entry = dictionary.update(perimeter, key, language(language), request.text(), request.version());
        return TextValue.of(entry, people(entry.updatedBy().stream().toList()));
    }

    /** {@code GET /v1/admin/texts/{key}/revisions}: needs {@code text:read}. */
    @GetMapping("/v1/admin/texts/{key}/revisions")
    Page<TextRevisionResponse> revisions(Perimeter perimeter, @PathVariable String key,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        PageResult<TextRevision> found = dictionary.revisions(perimeter, key, PageQuery.of(page, size));
        Map<UUID, PersonRef> people = people(found.items().stream()
                .filter(r -> r.actorType() == ActorType.PERSON).map(TextRevision::actorId)
                .collect(Collectors.toSet()));
        return page(found, r -> new TextRevisionResponse(r.id(), r.key(), r.language(), r.oldText().orElse(null),
                r.newText(), r.origin(), r.actorType() == ActorType.PERSON ? "PERSON" : "SYSTEM",
                people.get(r.actorId()), r.recordedAt()));
    }

    /** {@code POST /v1/admin/text-revisions/{id}/restore}: needs {@code text:update}. */
    @PostMapping("/v1/admin/text-revisions/{revisionId}/restore")
    TextValue restore(Perimeter perimeter, @PathVariable UUID revisionId) {
        TextEntry entry = dictionary.restore(perimeter, revisionId);
        return TextValue.of(entry, people(entry.updatedBy().stream().toList()));
    }

    /** {@code AR}, {@code EN} or {@code FR}, any case; {@code NOT_FOUND} otherwise. */
    private static Language language(String value) {
        try {
            return Language.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "Unknown language");
        }
    }

    /** Names of these people, by identifier. */
    private Map<UUID, PersonRef> people(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return identity.findPersons(ids.stream().map(PersonId::new).toList()).stream()
                .collect(Collectors.toMap(p -> p.id().value(), p -> new PersonRef(p.id().value(), p.firstName(),
                        p.lastName())));
    }

    /** A page of response bodies. */
    private static <S, T> Page<T> page(PageResult<S> result, Function<S, T> mapper) {
        return new Page<>(result.items().stream().map(mapper).toList(), new PageMeta(result.page(), result.size(),
                result.totalElements(), result.totalPages()));
    }
}
