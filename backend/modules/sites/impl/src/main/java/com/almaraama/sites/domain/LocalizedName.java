package com.almaraama.sites.domain;

/** A name in the three interface languages (M1). English falls back to French when the reference has none. */
public record LocalizedName(String ar, String en, String fr) {
}
