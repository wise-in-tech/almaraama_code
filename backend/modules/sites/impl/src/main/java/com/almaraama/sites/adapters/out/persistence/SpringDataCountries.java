package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.Country;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Countries of the reference list, read only. */
interface SpringDataCountries extends Repository<Country, String> {

    /** A country by ISO code. */
    Optional<Country> findById(String code);

    /** Every country by code. */
    List<Country> findAllByOrderByCodeAsc();
}
