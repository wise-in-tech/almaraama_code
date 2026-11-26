package com.almaraama.workflow.application.guards;

/**
 * A condition of the catalogue (ADR-036): a Spring component, tested once, usable by every circuit. Adding one never
 * changes the engine.
 */
public interface Guard {

    /** Code used in the circuit tables ({@code ReasonRequired}…). */
    String code();

    /**
     * Whether the passage may be offered to this actor before anything is typed (list of available passages). Input
     * conditions answer {@code true}.
     */
    default boolean allows(GuardCall call) {
        return true;
    }

    /** Checks the condition when the passage is taken; throws the contract's error code when it fails. */
    void check(GuardCall call);

    /** Whether the configuration of the condition is valid (publication check, ADR-036). */
    default boolean validConfig(java.util.Map<String, Object> config) {
        return true;
    }
}
