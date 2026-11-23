package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.PreRegistration;
import com.almaraama.membership.domain.PreRegistrationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Pre-registrations, schema membership. No delete (M2). */
interface SpringDataPreRegistrations extends Repository<PreRegistration, UUID> {

    /** Loads a pre-registration. */
    Optional<PreRegistration> findById(UUID id);

    /** The pre-registration whose current key has this hash. */
    Optional<PreRegistration> findByKeyHash(String keyHash);

    /** Every pre-registration of a site, newest first. */
    List<PreRegistration> findBySiteIdOrderByCreatedAtDesc(UUID siteId);

    /** Pre-registrations of a site with one stored status. */
    List<PreRegistration> findBySiteIdAndStatus(UUID siteId, PreRegistrationStatus status);

    /** Inserts or updates a pre-registration. */
    PreRegistration save(PreRegistration preRegistration);
}
