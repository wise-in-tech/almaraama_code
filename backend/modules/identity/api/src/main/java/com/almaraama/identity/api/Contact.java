package com.almaraama.identity.api;

/**
 * An e-mail and a phone number checked and written in their stored form (D1, ADR-028, ADR-030): the e-mail trimmed
 * and lower-cased, the phone in E.164.
 */
public record Contact(String email, String phone) {
}
