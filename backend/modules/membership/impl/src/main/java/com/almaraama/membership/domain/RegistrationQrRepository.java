package com.almaraama.membership.domain;

import com.almaraama.shared.id.SiteId;
import java.util.Optional;

/** Out port: registration QR codes of the sites (D2, ADR-034). */
public interface RegistrationQrRepository {

    /** The site's registration code, absent until first asked. */
    Optional<RegistrationQr> ofSite(SiteId siteId);

    /** The registration code with this value, already normalized (upper case, no dashes). */
    Optional<RegistrationQr> byCode(String normalizedCode);

    /** Stores a registration code. */
    RegistrationQr save(RegistrationQr registrationQr);
}
