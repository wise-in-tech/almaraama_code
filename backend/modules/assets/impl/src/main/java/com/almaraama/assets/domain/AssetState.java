package com.almaraama.assets.domain;

/** State of an equipment (G3). Archiving is separate ({@code archivedAt}, M2). Glossary {@code AssetState}. */
public enum AssetState {
    /** Works normally. */
    IN_SERVICE,
    /** Broken, waiting for a repair. */
    OUT_OF_ORDER,
    /** Taken out of service, still in place. */
    DECOMMISSIONED
}
