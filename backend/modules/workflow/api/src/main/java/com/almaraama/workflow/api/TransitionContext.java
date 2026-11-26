package com.almaraama.workflow.api;

import com.almaraama.shared.actor.Actor;
import java.util.function.Predicate;

/**
 * Who wants to take a passage, as the calling module knows it (ADR-036): the engine knows neither roles nor tickets.
 *
 * @param actor             the person or the system
 * @param actorIsRequester  the actor asked for the subject (C6, {@code IsRequester})
 * @param requesterIsPerson the subject was asked by a person, not the system (requester validation, H5)
 * @param hasPermission     whether the actor holds a permission code ({@code ticket:close}) on the subject's site,
 *                          computed from the {@code Perimeter}
 */
public record TransitionContext(Actor actor, boolean actorIsRequester, boolean requesterIsPerson,
                                Predicate<String> hasPermission) {
}
