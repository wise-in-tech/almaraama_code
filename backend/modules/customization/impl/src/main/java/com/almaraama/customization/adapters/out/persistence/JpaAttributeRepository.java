package com.almaraama.customization.adapters.out.persistence;

import com.almaraama.customization.domain.AttributeDefinition;
import com.almaraama.customization.domain.AttributeOption;
import com.almaraama.customization.domain.AttributeRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link AttributeRepository} on the {@code customization} tables. */
@Component
class JpaAttributeRepository implements AttributeRepository {

    private final SpringDataAttributeDefinitions definitions;
    private final SpringDataAttributeOptions options;

    /**
     * @param definitions Spring Data repository of the definitions
     * @param options     Spring Data repository of the options
     */
    JpaAttributeRepository(SpringDataAttributeDefinitions definitions, SpringDataAttributeOptions options) {
        this.definitions = definitions;
        this.options = options;
    }

    /** Loads a definition. */
    @Override
    public Optional<AttributeDefinition> findById(UUID id) {
        return definitions.findById(id);
    }

    /** The platform's definitions of the family, and the organization's when given. */
    @Override
    public List<AttributeDefinition> ofFamily(UUID familyId, Optional<UUID> organizationId, boolean includeArchived) {
        return organizationId.map(org -> definitions.ofFamily(familyId, org, includeArchived))
                .orElseGet(() -> definitions.platformOfFamily(familyId, includeArchived));
    }

    /** Whether a visible definition of the family already uses the code. */
    @Override
    public boolean codeTaken(UUID familyId, Optional<UUID> organizationId, String code) {
        return ofFamily(familyId, organizationId, true).stream().anyMatch(d -> d.code().equals(code));
    }

    /** The options of several definitions; an empty input gives an empty list without querying. */
    @Override
    public List<AttributeOption> optionsOf(Collection<UUID> definitionIds) {
        return definitionIds.isEmpty() ? List.of()
                : options.findByDefinitionIdInOrderBySortOrderAscCodeAsc(definitionIds);
    }

    /** Stores a definition. */
    @Override
    public AttributeDefinition save(AttributeDefinition definition) {
        return definitions.save(definition);
    }

    /** Stores an option. */
    @Override
    public AttributeOption save(AttributeOption option) {
        return options.save(option);
    }
}
