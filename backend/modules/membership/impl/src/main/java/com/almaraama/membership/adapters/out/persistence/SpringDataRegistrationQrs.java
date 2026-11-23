package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.RegistrationQr;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Registration QR codes of the sites, schema membership. No delete (M2). */
interface SpringDataRegistrationQrs extends Repository<RegistrationQr, UUID> {

    /** The registration code of a site, keyed by the site's identifier. */
    Optional<RegistrationQr> findById(UUID siteId);

    /** The registration code with this stored value. */
    Optional<RegistrationQr> findByCode(String code);

    /** Inserts or updates a registration code. */
    RegistrationQr save(RegistrationQr registrationQr);
}
