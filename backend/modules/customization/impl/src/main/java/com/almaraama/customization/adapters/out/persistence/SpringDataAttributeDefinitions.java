package com.almaraama.customization.adapters.out.persistence;

import com.almaraama.customization.domain.AttributeDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code customization.attribute_definition}; no delete (M2). */
interface SpringDataAttributeDefinitions extends Repository<AttributeDefinition, UUID> {

    /** Loads a definition. */
    Optional<AttributeDefinition> findById(UUID id);

    /** The platform's definitions of a family, and the organization's when given. */
    @Query("select d from AttributeDefinition d where d.familyId = :family "
            + "and (d.organizationId is null or d.organizationId = :organization) "
            + "and (:archived = true or d.active = true) order by d.sortOrder, d.code")
    List<AttributeDefinition> ofFamily(@Param("family") UUID family, @Param("organization") UUID organization,
                                       @Param("archived") boolean archived);

    /** The platform's definitions of a family only. */
    @Query("select d from AttributeDefinition d where d.familyId = :family and d.organizationId is null "
            + "and (:archived = true or d.active = true) order by d.sortOrder, d.code")
    List<AttributeDefinition> platformOfFamily(@Param("family") UUID family, @Param("archived") boolean archived);

    /** Inserts or updates a definition. */
    AttributeDefinition save(AttributeDefinition definition);
}
