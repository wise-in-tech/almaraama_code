package com.almaraama.membership.domain;

import com.almaraama.shared.id.SiteId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: pre-registrations (D3). No delete (M2). */
public interface PreRegistrationRepository {

    /** One pre-registration. */
    Optional<PreRegistration> findById(UUID id);

    /** The pre-registration whose current key has this SHA-256 hash. */
    Optional<PreRegistration> byKeyHash(String keyHash);

    /** Every pre-registration of a site, newest first. */
    List<PreRegistration> ofSite(SiteId siteId);

    /** The stored-pending pre-registrations of a site (expired ones included; filter with the clock). */
    List<PreRegistration> pendingOfSite(SiteId siteId);

    /** Inserts or updates a pre-registration. */
    PreRegistration save(PreRegistration preRegistration);
}
