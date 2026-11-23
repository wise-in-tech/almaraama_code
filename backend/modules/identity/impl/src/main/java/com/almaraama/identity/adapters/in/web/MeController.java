package com.almaraama.identity.adapters.in.web;

import com.almaraama.identity.adapters.in.web.IdentityWebDtos.MeResponse;
import com.almaraama.identity.api.IdentityApi;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.perimeter.Perimeter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person. The perimeter is resolved from the access token and passed as a parameter (CF-11). */
@RestController
class MeController {

    private final IdentityApi identity;

    /** @param identity reads the signed-in person's profile */
    MeController(IdentityApi identity) {
        this.identity = identity;
    }

    /** {@code GET /v1/me} (D1): the signed-in person's profile. */
    @GetMapping("/v1/me")
    MeResponse me(Perimeter perimeter) {
        return identity.findPerson(new PersonId(perimeter.actor().id()))
                .map(p -> new MeResponse(p.id().value(), p.firstName(), p.lastName(), p.email(), p.emailVerifiedAt(),
                        p.phone(), p.language(), p.status()))
                .orElseThrow(() -> new BusinessException(
                        "UNAUTHENTICATED", ErrorCategory.UNAUTHENTICATED, "Person no longer exists"));
    }
}
