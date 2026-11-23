package com.almaraama.app.web;

import com.almaraama.membership.api.PerimeterApi;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.perimeter.Perimeter;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Gives every controller method that asks for it the caller's {@link Perimeter}, built for this request from the
 * verified access token by the membership module (CF-11, ADR-026). Never cached: a removed role or permission
 * applies on the next request (RB-4).
 */
class PerimeterArgumentResolver implements HandlerMethodArgumentResolver {

    private final PerimeterApi perimeters;

    /** @param perimeters the membership module's port that computes a person's perimeter */
    PerimeterArgumentResolver(PerimeterApi perimeters) {
        this.perimeters = perimeters;
    }

    /** Applies to every controller parameter of type {@link Perimeter}. */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Perimeter.class.equals(parameter.getParameterType());
    }

    /**
     * Builds the caller's perimeter from the subject of the verified access token; {@code UNAUTHENTICATED} when the
     * request carries none. Puts the open support session, if any, in the logging context.
     */
    @Override
    public Perimeter resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                     NativeWebRequest request, WebDataBinderFactory binderFactory) {
        if (request.getUserPrincipal() instanceof JwtAuthenticationToken token) {
            Perimeter perimeter = perimeters.perimeterOf(PersonId.of(token.getToken().getSubject()));
            // ADR-035: every log line of a request made in a support session carries the session (ADR-033).
            perimeter.actor().supportSession().ifPresent(id -> MDC.put("supportSessionId", id.toString()));
            return perimeter;
        }
        throw new BusinessException("UNAUTHENTICATED", ErrorCategory.UNAUTHENTICATED, "No authenticated caller");
    }
}
