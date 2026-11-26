package com.almaraama.customization.adapters.out.persistence;

import com.almaraama.customization.domain.AttributeOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code customization.attribute_option}; no delete (M2). */
interface SpringDataAttributeOptions extends Repository<AttributeOption, UUID> {

    /** The options of several definitions, by sort order. */
    List<AttributeOption> findByDefinitionIdInOrderBySortOrderAscCodeAsc(Collection<UUID> definitionIds);

    /** Inserts or updates an option. */
    AttributeOption save(AttributeOption option);
}
