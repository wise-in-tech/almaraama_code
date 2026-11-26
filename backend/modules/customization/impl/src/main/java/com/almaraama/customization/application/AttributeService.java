package com.almaraama.customization.application;

import com.almaraama.customization.api.AttributeDataType;
import com.almaraama.customization.api.AttributeDefinitionView;
import com.almaraama.customization.api.AttributesApi;
import com.almaraama.customization.domain.AttributeDefinition;
import com.almaraama.customization.domain.AttributeOption;
import com.almaraama.customization.domain.AttributeRepository;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Characteristic definitions and validation of their values (G3, ADR-039). Permissions are checked by the module that
 * owns the target (assets) before calling.
 */
@Service
class AttributeService implements AttributesApi {

    private static final Logger LOG = LoggerFactory.getLogger(AttributeService.class);
    private static final int MAX_OPTIONS = 50;
    private static final int MAX_TEXT = 500;

    private final AttributeRepository attributes;
    private final Clock clock;

    /**
     * @param attributes the definition and option tables
     * @param clock      time of changes
     */
    AttributeService(AttributeRepository attributes, Clock clock) {
        this.attributes = attributes;
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AttributeDefinitionView> definitions(UUID familyId, Optional<UUID> organizationId,
                                                     boolean includeArchived) {
        return views(attributes.ofFamily(familyId, organizationId, includeArchived));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<AttributeDefinitionView> find(UUID definitionId) {
        return attributes.findById(definitionId).map(d -> views(List.of(d)).getFirst());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AttributeDefinitionView create(NewDefinition command) {
        if (attributes.codeTaken(command.familyId(), command.organizationId(), command.code())) {
            throw new BusinessException("ATTRIBUTE_CODE_TAKEN", ErrorCategory.CONFLICT, "Code used in the family");
        }
        checkOptions(command.dataType(), command.options());
        Instant now = clock.instant();
        AttributeDefinition definition = attributes.save(AttributeDefinition.create(command.scope(),
                command.organizationId(), command.familyId(), command.code(), command.dataType(), command.required(),
                command.label(), command.unit(), command.sortOrder(), now));
        int order = 0;
        for (OptionInput option : command.options()) {
            attributes.save(AttributeOption.create(definition.id(), option.code(), option.label(), order++,
                    option.active()));
        }
        LOG.debug("Characteristic {} ({}) added to family {}", command.code(), command.dataType(), command.familyId());
        return views(List.of(definition)).getFirst();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public AttributeDefinitionView update(UUID definitionId, DefinitionChange change) {
        AttributeDefinition definition = attributes.findById(definitionId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "No definition"));
        if (definition.version() != change.version()) {
            throw new BusinessException("VERSION_CONFLICT", ErrorCategory.CONFLICT, "Changed in the meantime");
        }
        checkOptions(definition.dataType(), change.options());
        definition.change(change.required(), change.label(), change.unit(), change.sortOrder(), change.active(),
                clock.instant());
        attributes.save(definition);
        Map<String, AttributeOption> existing = attributes.optionsOf(List.of(definitionId)).stream()
                .collect(Collectors.toMap(AttributeOption::code, Function.identity()));
        int order = 0;
        Set<String> kept = new HashSet<>();
        for (OptionInput input : change.options()) {
            kept.add(input.code());
            AttributeOption option = existing.get(input.code());
            if (option == null) {
                attributes.save(AttributeOption.create(definitionId, input.code(), input.label(), order++,
                        input.active()));
            } else {
                option.change(input.label(), order++, input.active());
                attributes.save(option);
            }
        }
        for (AttributeOption option : existing.values()) {
            if (!kept.contains(option.code()) && option.active()) {
                option.change(option.label(), option.sortOrder(), false);
                attributes.save(option);
            }
        }
        return views(List.of(definition)).getFirst();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> validate(UUID familyId, UUID organizationId, Map<String, Object> values) {
        List<AttributeDefinitionView> definitions = definitions(familyId, Optional.of(organizationId), false);
        Map<String, AttributeDefinitionView> byCode = definitions.stream()
                .collect(Collectors.toMap(AttributeDefinitionView::code, Function.identity()));
        Set<String> wrong = new TreeSet<>();
        Map<String, Object> cleaned = new LinkedHashMap<>();
        Map<String, Object> input = values == null ? Map.of() : values;
        input.forEach((code, raw) -> {
            AttributeDefinitionView definition = byCode.get(code);
            if (definition == null) {
                wrong.add(code);
                return;
            }
            Optional<Object> value = clean(definition, raw);
            if (value == null) {
                wrong.add(code);
            } else {
                value.ifPresent(v -> cleaned.put(code, v));
            }
        });
        for (AttributeDefinitionView definition : definitions) {
            if (definition.required() && !cleaned.containsKey(definition.code())) {
                wrong.add(definition.code());
            }
        }
        if (!wrong.isEmpty()) {
            throw new BusinessException("ATTRIBUTE_INVALID", ErrorCategory.RULE_VIOLATION, "Characteristics refused",
                    List.copyOf(wrong));
        }
        return cleaned;
    }

    /**
     * The cleaned value: empty for a blank value, the value for a valid one, {@code null} for a value of the wrong type
     * (a deliberate tri-state, private to this class).
     */
    private static Optional<Object> clean(AttributeDefinitionView definition, Object raw) {
        if (raw == null || (raw instanceof String text && text.isBlank())) {
            return Optional.empty();
        }
        return switch (definition.dataType()) {
            case TEXT -> raw instanceof String text && text.strip().length() <= MAX_TEXT
                    ? Optional.of(text.strip()) : null;
            case INTEGER -> raw instanceof Number n && new BigDecimal(n.toString()).stripTrailingZeros().scale() <= 0
                    ? Optional.of(n.longValue()) : null;
            case DECIMAL -> raw instanceof Number n ? Optional.of(new BigDecimal(n.toString())) : null;
            case BOOLEAN -> raw instanceof Boolean b ? Optional.of(b) : null;
            case DATE -> date(raw);
            case LIST -> raw instanceof String text && definition.options().stream()
                    .anyMatch(o -> o.active() && o.code().equals(text)) ? Optional.of(text) : null;
        };
    }

    /** A {@code YYYY-MM-DD} date, or {@code null} when invalid. */
    private static Optional<Object> date(Object raw) {
        if (!(raw instanceof String text)) {
            return null;
        }
        try {
            return Optional.of(LocalDate.parse(text.strip()).toString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** A {@code LIST} needs one to fifty distinct options; other types none. {@code ATTRIBUTE_OPTIONS_INVALID}. */
    private static void checkOptions(AttributeDataType type, List<OptionInput> options) {
        boolean list = type == AttributeDataType.LIST;
        long distinct = options.stream().map(OptionInput::code).distinct().count();
        if ((list && (options.isEmpty() || options.size() > MAX_OPTIONS)) || (!list && !options.isEmpty())
                || distinct != options.size()) {
            throw new BusinessException("ATTRIBUTE_OPTIONS_INVALID", ErrorCategory.VALIDATION, "Options of a list");
        }
    }

    /** The views of definitions with their options, in one query for the options. */
    private List<AttributeDefinitionView> views(List<AttributeDefinition> definitions) {
        Map<UUID, List<AttributeOption>> options = attributes
                .optionsOf(definitions.stream().map(AttributeDefinition::id).toList()).stream()
                .collect(Collectors.groupingBy(AttributeOption::definitionId));
        List<AttributeDefinitionView> views = new ArrayList<>();
        for (AttributeDefinition d : definitions) {
            List<AttributeDefinitionView.Option> choices = options.getOrDefault(d.id(), List.of()).stream()
                    .map(o -> new AttributeDefinitionView.Option(o.code(), o.label(), o.active())).toList();
            views.add(new AttributeDefinitionView(d.id(), d.ownerScope(), d.organizationId(), d.familyId(), d.code(),
                    d.dataType(), d.required(), d.label(), d.unit(), choices, d.sortOrder(), d.active(),
                    d.version()));
        }
        return views;
    }
}
