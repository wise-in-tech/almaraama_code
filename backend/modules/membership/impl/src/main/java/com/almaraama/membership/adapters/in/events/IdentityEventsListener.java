package com.almaraama.membership.adapters.in.events;

import com.almaraama.identity.api.events.PersonActivatedV1;
import com.almaraama.identity.api.events.EmailVerifiedV1;
import com.almaraama.membership.application.MembershipService;
import com.almaraama.membership.application.RegistrationService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to the identity module: an invited resident chose their password, their access becomes active (D2); a
 * person who registered by QR code verified their e-mail, their request reaches the managers (ADR-034).
 */
@Component
class IdentityEventsListener {

    private final MembershipService memberships;
    private final RegistrationService registrations;

    /**
     * @param memberships   activates the invited person's accesses
     * @param registrations submits the person's QR code registrations
     */
    IdentityEventsListener(MembershipService memberships, RegistrationService registrations) {
        this.memberships = memberships;
        this.registrations = registrations;
    }

    /** D2: the person chose their password; their pending invitations become active resident accesses. */
    @ApplicationModuleListener
    void on(PersonActivatedV1 event) {
        memberships.activateInvitations(event.personId());
    }

    /** ADR-034: the e-mail is verified; the person's QR code registrations are submitted (K1) or approved (D3). */
    @ApplicationModuleListener
    void on(EmailVerifiedV1 event) {
        registrations.emailVerified(event.personId());
    }
}
