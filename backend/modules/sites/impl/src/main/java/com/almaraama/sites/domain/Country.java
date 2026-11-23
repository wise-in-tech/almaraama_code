package com.almaraama.sites.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;

/**
 * A country of the reference data, as a set of parameters (A8): named administrative levels, the attachment level of
 * addresses ("city"), currency (M5), time zone, languages, calling code and working days. Adding a country adds rows,
 * never a table or a column. Read only for the application; loaded by the platform administrators (C12, M7).
 */
@Entity
@Table(schema = "sites", name = "country")
public class Country {

    /** A named level of the country's zone tree (Algeria: 1 wilaya, 2 commune). */
    @Embeddable
    public static class Level {

        @Column(name = "level", nullable = false)
        private int level;

        @Column(name = "name_ar", nullable = false)
        private String nameAr;

        @Column(name = "name_en", nullable = false)
        private String nameEn;

        @Column(name = "name_fr", nullable = false)
        private String nameFr;

        /** For JPA only. */
        protected Level() {
        }

        /** Number of the level, from 1 (top). */
        public int level() {
            return level;
        }

        /** Name of the level in the three languages. */
        public LocalizedName name() {
            return new LocalizedName(nameAr, nameEn, nameFr);
        }
    }

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_fr", nullable = false)
    private String nameFr;

    @Column(name = "attachment_level", nullable = false)
    private int attachmentLevel;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @Column(name = "languages", nullable = false)
    private String languages;

    @Column(name = "calling_code", nullable = false)
    private String callingCode;

    @Column(name = "working_days", nullable = false)
    private String workingDays;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "sites", name = "country_level", joinColumns = @JoinColumn(name = "country_code"))
    @OrderBy("level")
    private List<Level> levels;

    /** For JPA only. */
    protected Country() {
    }

    /** ISO 3166-1 alpha-2 code. */
    public String code() {
        return code;
    }

    /** Name in the three languages. */
    public LocalizedName name() {
        return new LocalizedName(nameAr, nameEn, nameFr);
    }

    /** The level an address is attached to: 2 (commune) for Algeria (A8). */
    public int attachmentLevel() {
        return attachmentLevel;
    }

    /** ISO 4217 currency of the amounts of its organizations (M5). */
    public String currencyCode() {
        return currencyCode;
    }

    /** IANA time zone. */
    public String timeZone() {
        return timeZone;
    }

    /** Interface languages, in display order (codes {@code AR}, {@code EN}, {@code FR}). */
    public List<String> languages() {
        return split(languages);
    }

    /** Default international calling code, such as {@code +213}. */
    public String callingCode() {
        return callingCode;
    }

    /** Working days, such as {@code SUN} to {@code THU}. */
    public List<String> workingDays() {
        return split(workingDays);
    }

    /** The named levels, from 1 (top). */
    public List<Level> levels() {
        return List.copyOf(levels);
    }

    /** Splits a comma-separated column into trimmed values. */
    private static List<String> split(String value) {
        return Arrays.stream(value.split(",")).map(String::strip).filter(v -> !v.isEmpty()).toList();
    }
}
