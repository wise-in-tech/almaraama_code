package com.almaraama.identity.adapters.in.events;

import com.almaraama.identity.api.events.AccountEmailRequestedV1;
import com.almaraama.identity.application.AccountEmailDispatcher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Sends the requested account e-mails once the request is committed (ADR-031). {@code @ApplicationModuleListener}
 * runs asynchronously after the commit, in a new transaction, and marks the outbox entry complete only on success, so
 * a slow relay never delays the answer and a failure leaves the entry to be resubmitted (every 5 minutes and at
 * restart).
 */
@Component
class AccountEmailListener {

    private final AccountEmailDispatcher dispatcher;

    /** @param dispatcher creates the link, writes and sends the e-mail */
    AccountEmailListener(AccountEmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Hands the request to the dispatcher. */
    @ApplicationModuleListener
    void on(AccountEmailRequestedV1 request) {
        dispatcher.dispatch(request);
    }
}
