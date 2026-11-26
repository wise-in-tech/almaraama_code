package com.almaraama.workflow.api;

import com.almaraama.shared.i18n.LocalizedText;

/**
 * A step of a circuit as other modules see it.
 *
 * @param code     step code in its circuit ({@code REQUESTED}…)
 * @param category fixed category
 * @param label    the client's label, else the circuit's (H5), in every language
 */
public record NodeState(String code, NodeCategory category, LocalizedText label) {
}
