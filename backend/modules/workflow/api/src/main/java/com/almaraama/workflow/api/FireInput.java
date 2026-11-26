package com.almaraama.workflow.api;

import java.util.Optional;

/** What the actor typed when taking a passage (H5: reason of a hold; H6: reason of a cancellation). */
public record FireInput(Optional<String> reason, Optional<String> comment) {

    /** Nothing typed (automatic passages). */
    public static FireInput none() {
        return new FireInput(Optional.empty(), Optional.empty());
    }
}
