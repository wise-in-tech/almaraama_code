package com.almaraama.shared.access;

/**
 * Where a role or a permission applies (ADR-026). A role or permission on an organization covers all its sites.
 * Glossary {@code ScopeType}.
 */
public enum ScopeType {
    PLATFORM,
    ORGANIZATION,
    SITE
}
