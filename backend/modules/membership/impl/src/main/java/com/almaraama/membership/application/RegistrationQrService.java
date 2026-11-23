package com.almaraama.membership.application;

import com.almaraama.membership.api.events.RegistrationQrEnabledChangedV1;
import com.almaraama.membership.api.events.RegistrationQrRotatedV1;
import com.almaraama.membership.domain.RegistrationQr;
import com.almaraama.membership.domain.RegistrationQrRepository;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.OrganizationSummary;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration QR codes of the sites (D2, criterion 4, ADR-034): printed at the entrance; managed by the promoteur
 * ({@code registration-qr:manage}), seen by the managers ({@code registration-qr:read}). The list of sites is never
 * public; unknown, replaced and deactivated codes give the same answer.
 */
@Service
public class RegistrationQrService {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationQrService.class);

    /** What a person sees before registering: only the names of the site and of its organization. */
    public record Preview(String siteName, String organizationName) {
    }

    private final RegistrationQrRepository codes;
    private final SitesApi sites;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param codes  the registration codes
     * @param sites  site and organization names
     * @param events publishes the changes for the audit log
     * @param clock  time of changes
     */
    RegistrationQrService(RegistrationQrRepository codes, SitesApi sites, ApplicationEventPublisher events,
                          Clock clock) {
        this.codes = codes;
        this.sites = sites;
        this.events = events;
        this.clock = clock;
    }

    /** The site's code, created active on first request; needs {@code registration-qr:read} (D2). */
    @Transactional
    public RegistrationQr get(Perimeter perimeter, SiteId siteId) {
        return code(perimeter, siteId, Permission.REGISTRATION_QR_READ);
    }

    /**
     * Activates or deactivates registration on the site; needs {@code registration-qr:manage} (the promoteur, D2);
     * {@code VERSION_CONFLICT}. Publishes {@code RegistrationQrEnabledChangedV1} when it changes.
     */
    @Transactional
    public RegistrationQr setEnabled(Perimeter perimeter, SiteId siteId, boolean enabled, long version) {
        RegistrationQr qr = code(perimeter, siteId, Permission.REGISTRATION_QR_MANAGE);
        if (qr.version() != version) {
            throw new BusinessException("VERSION_CONFLICT", ErrorCategory.CONFLICT, "Changed in the meantime");
        }
        if (qr.enabled() != enabled) {
            Instant now = clock.instant();
            qr.enable(enabled, now);
            events.publishEvent(new RegistrationQrEnabledChangedV1(siteId, qr.organizationId(), enabled,
                    perimeter.actor(), now));
            LOG.debug("Registration QR of site {} {}", siteId.value(), enabled ? "activated" : "deactivated");
        }
        return codes.save(qr);
    }

    /**
     * Replaces the code; the old one stops working at once (criterion 4); needs {@code registration-qr:manage}.
     * Publishes {@code RegistrationQrRotatedV1}.
     */
    @Transactional
    public RegistrationQr rotate(Perimeter perimeter, SiteId siteId) {
        RegistrationQr qr = code(perimeter, siteId, Permission.REGISTRATION_QR_MANAGE);
        Instant now = clock.instant();
        qr.rotate(now);
        events.publishEvent(new RegistrationQrRotatedV1(siteId, qr.organizationId(), perimeter.actor(), now));
        LOG.debug("Registration QR of site {} replaced", siteId.value());
        return codes.save(qr);
    }

    /** Public preview before registering; {@code REGISTRATION_CODE_INVALID} for unknown or closed codes. */
    @Transactional(readOnly = true)
    public Preview inspect(String typed) {
        SiteSummary site = resolve(typed);
        String organization = sites.findOrganization(site.organizationId()).map(OrganizationSummary::name)
                .orElse("");
        return new Preview(site.name(), organization);
    }

    /**
     * The open site behind a typed code: the code exists and is active, the site and its organization are active;
     * otherwise {@code REGISTRATION_CODE_INVALID}, the same answer in every case. Never logs the code.
     */
    SiteSummary resolve(String typed) {
        return codes.byCode(RegistrationQr.normalize(typed))
                .filter(RegistrationQr::enabled)
                .flatMap(code -> sites.findSite(code.siteId()))
                .filter(SiteSummary::active)
                .filter(site -> sites.findOrganization(site.organizationId()).map(OrganizationSummary::active)
                        .orElse(false))
                .orElseThrow(() -> {
                    LOG.debug("Registration code refused (unknown, replaced or deactivated)");
                    return new BusinessException("REGISTRATION_CODE_INVALID", ErrorCategory.NOT_FOUND,
                            "Unknown or closed registration code");
                });
    }

    /** The site's code after checking the permission, created when it does not exist yet. */
    private RegistrationQr code(Perimeter perimeter, SiteId siteId, Permission permission) {
        SiteSummary site = sites.findSite(siteId).orElseThrow(RoleAdministration::notFound);
        perimeter.require(permission, site.organizationId(), siteId);
        return codes.ofSite(siteId).orElseGet(() -> codes.save(RegistrationQr.create(site.organizationId(), siteId,
                clock.instant())));
    }
}
