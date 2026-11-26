package com.almaraama.sites.api;

import com.almaraama.shared.i18n.LocalizedText;
import java.util.Optional;

/**
 * The current address of a building or a site (A9), for display next to tickets and lists (criterion 18).
 *
 * @param zoneName name of the attachment zone (Algeria: the commune), in every language
 */
public record AddressSummary(Optional<String> label, String addressLine, Optional<String> postalCode,
                             LocalizedText zoneName) {
}
