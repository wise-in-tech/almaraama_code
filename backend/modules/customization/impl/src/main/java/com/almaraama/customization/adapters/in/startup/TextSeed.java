package com.almaraama.customization.adapters.in.startup;

import com.almaraama.customization.application.TextDictionary;
import com.almaraama.customization.domain.DeliveredTexts;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads the texts delivered with the release into the dictionary at start-up (ADR-029); never overwrites a text an
 * administrator edited. Idempotent: a restart without new texts changes nothing.
 */
@Component
class TextSeed implements ApplicationRunner {

    private final DeliveredTexts delivered;
    private final TextDictionary dictionary;

    /**
     * @param delivered  the texts of the release
     * @param dictionary applies them
     */
    TextSeed(DeliveredTexts delivered, TextDictionary dictionary) {
        this.delivered = delivered;
        this.dictionary = dictionary;
    }

    /** Applies the delivered texts once the application has started. */
    @Override
    public void run(ApplicationArguments args) {
        dictionary.seed(delivered.load());
    }
}
