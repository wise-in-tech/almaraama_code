package com.almaraama.shared.actor;

/** Kinds of actors (glossary). Only {@code PERSON} acts in P1; the others are prepared for integration and AI. */
public enum ActorType {
    /** A human, signed in with phone and PIN. */
    PERSON,
    /** The platform itself (scheduled job) or a partner system (SAP, ServiceNow). */
    SYSTEM,
    /** An AI agent: audited, limited to its perimeter, approved by humans through the workflow. */
    AGENT
}
