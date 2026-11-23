package com.almaraama.sites.domain;

import java.util.List;
import java.util.Optional;

/** Out port: countries of the reference data (A8), read only for the application. */
public interface ReferenceData {

    /** Every country of the reference data, by code. */
    List<Country> countries();

    /** A country by ISO code. */
    Optional<Country> findCountry(String code);
}
