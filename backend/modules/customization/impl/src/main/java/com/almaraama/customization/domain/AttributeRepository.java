package com.almaraama.customization.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: characteristic definitions and their options (G3). Never deleted (M2). */
public interface AttributeRepository {

    /** Loads a definition. */
    Optional<AttributeDefinition> findById(UUID id);

    /**
     * The definitions of a family visible to an organization: the platform's, and the organization's when given; by sort
     * order then code.
     */
    List<AttributeDefinition> ofFamily(UUID familyId, Optional<UUID> organizationId, boolean includeArchived);

    /** Whether a definition of the family visible to the organization (or a platform one) already has the code. */
    boolean codeTaken(UUID familyId, Optional<UUID> organizationId, String code);

    /** The options of several definitions, by sort order. */
    List<AttributeOption> optionsOf(Collection<UUID> definitionIds);

    /** Stores a definition. */
    AttributeDefinition save(AttributeDefinition definition);

    /** Stores an option. */
    AttributeOption save(AttributeOption option);
}
