package com.almaraama.identity.domain;

/**
 * Out port: sends an e-mail (ADR-031). The SMTP adapter talks to the relay; tests record the messages. A failure
 * throws, so that the caller's transaction (which created the link) is rolled back and the request retried.
 */
public interface EmailSender {

    /** Sends the message; throws when the relay refuses it or cannot be reached. */
    void send(EmailMessage message);
}
