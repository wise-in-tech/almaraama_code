package com.almaraama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.almaraama.membership.application.PlatformBootstrap;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Sprint 2 journeys of spec v3.4 through the HTTP API on a real database: Excel import of units with their category
 * (criterion 1, ADR-027), registration QR code (criterion 4), registration by QR code and manual approval with units
 * (criterion 5, D2, K1), pre-registration with a single-use key (criterion 6, D3), resident import, default
 * typologies (criterion 2), provisional zones validated or merged (A8), support sessions and deactivated
 * organizations (C12, ADR-035), and the text dictionary edited without deployment (criterion 20, ADR-029).
 * E-mails are recorded, never sent.
 */
@SpringBootTest(properties = "almaraama.security.auth-requests-per-minute=10000")
@AutoConfigureMockMvc
class Sprint2FlowTests extends PostgresIntegrationTest {

    private static final String PASSWORD = "Une résidence bien tenue";
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    MockMvc mvc;

    @Autowired
    PlatformBootstrap bootstrap;

    @Autowired
    RecordingEmailSender mail;

    @Autowired
    JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------------------- criterion 1

    @Test
    void criterion1_hundredUnitsWithTheirCategoryAreImportedWithAReadableReport() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        createBuilding(org.owner(), site, "Bâtiment A");
        byte[] template = mvc.perform(get("/v1/units/import-template?language=FR&siteId=" + site)
                        .header(HttpHeaders.AUTHORIZATION, org.owner()))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX.toString()))
                .andReturn().getResponse().getContentAsByteArray();

        List<Map<String, String>> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= 98; i++) {
            rows.add(Map.of("reference", "A-" + i, "type", "APARTMENT", "category", "T3", "building", "Bâtiment A",
                    "floor", String.valueOf(i % 5)));
        }
        rows.add(Map.of("reference", "P-1", "type", "PARKING", "category", "COVERED", "annexOf", "A-1"));
        rows.add(Map.of("reference", "C-1", "type", "STORAGE", "category", "CELLAR", "annexOf", "A-2"));

        List<Map<String, String>> withErrors = new java.util.ArrayList<>(rows);
        withErrors.add(Map.of("reference", "A-1", "type", "APARTMENT", "category", "T3"));
        withErrors.add(Map.of("reference", "X-1", "type", "VILLA", "category", "T3"));
        withErrors.add(Map.of("reference", "X-2", "type", "APARTMENT", "category", "T9", "building", "Nulle part"));
        upload(site, fill(template, withErrors), false, org.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.errors[?(@.code=='IMPORT_DUPLICATE_IN_FILE')].row").value(103))
                .andExpect(jsonPath("$.errors[?(@.code=='IMPORT_VALUE_INVALID')].column").value("type"))
                .andExpect(jsonPath("$.errors[?(@.code=='IMPORT_CATEGORY_UNKNOWN')].value").value("T9"))
                .andExpect(jsonPath("$.errors[?(@.code=='IMPORT_BUILDING_UNKNOWN')].column").value("building"));
        assertThat(units(site)).isZero();

        byte[] good = fill(template, rows);
        upload(site, good, true, org.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CHECKED")).andExpect(jsonPath("$.totalRows").value(100));
        assertThat(units(site)).isZero();

        Instant started = Instant.now();
        upload(site, good, false, org.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IMPORTED")).andExpect(jsonPath("$.createdCount").value(100));
        assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofMinutes(2));
        assertThat(units(site)).isEqualTo(100);
    }

    @Test
    void ADR027_aFileThatIsNotAnXlsxIsRefusedByItsContent() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        upload(site, "reference;type\nA-1;APARTMENT".getBytes(), false, org.owner())
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("IMPORT_FILE_INVALID"));
    }

    // ---------------------------------------------------------------------------------------- criterion 4

    @Test
    void criterion4_thePromoteurReplacesAndDeactivatesTheRegistrationQrCode() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String manager = inviteManager(org, site);

        String qr = body(get("/v1/sites/" + site + "/registration-qr"), manager);
        String first = JsonPath.read(qr, "$.code");
        assertThat((String) JsonPath.read(qr, "$.registrationUrl")).endsWith("/r#" + first.replace("-", ""));
        call(post("/v1/sites/" + site + "/registration-qr/rotate"), manager).andExpect(status().isForbidden());
        inspect(first).andExpect(status().isOk()).andExpect(jsonPath("$.siteName").value("Résidence Les Pins"));

        String rotated = body(post("/v1/sites/" + site + "/registration-qr/rotate"), org.owner());
        String second = JsonPath.read(rotated, "$.code");
        assertThat(second).isNotEqualTo(first);
        inspect(first).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REGISTRATION_CODE_INVALID"));
        inspect(second).andExpect(status().isOk());

        Number version = JsonPath.read(rotated, "$.version");
        call(put("/v1/sites/" + site + "/registration-qr").content("""
                {"enabled":false,"version":%d}""".formatted(version.longValue())), org.owner())
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        inspect(second).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REGISTRATION_CODE_INVALID"));
        await(() -> count("SELECT count(*) FROM audit.audit_entry WHERE action LIKE 'REGISTRATION_QR_%' "
                + "AND subject_id = '" + site + "'") >= 2);
    }

    // ----------------------------------------------------------------------------------- criterion 5, D2

    @Test
    void criterion5_aRegisteredPersonSeesNothingUntilTheManagerGivesTwoApartmentsAParkingAndTwoCellars()
            throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "Bâtiment A");
        String manager = inviteManager(org, site);
        List<String> units = List.of(createUnit(org.owner(), site, "A-1", "APARTMENT", "T3", building),
                createUnit(org.owner(), site, "A-2", "APARTMENT", "T2", building),
                createUnit(org.owner(), site, "P-1", "PARKING", "COVERED", building),
                createUnit(org.owner(), site, "C-1", "STORAGE", "CELLAR", building),
                createUnit(org.owner(), site, "C-2", "STORAGE", "CELLAR", building));
        String code = JsonPath.read(body(get("/v1/sites/" + site + "/registration-qr"), org.owner()), "$.code");

        String email = newEmail();
        register(code, email, "Appartements A-1 et A-2, parking P-1, caves C-1 et C-2", null)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("CHECK_YOUR_EMAIL"));
        assertThat(pendingOf(site, manager)).isEmpty();
        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        verifyEmail(email);
        await(() -> body(get("/v1/notifications"), manager).contains("ACCESS_REQUESTED"));
        String pending = body(get("/v1/sites/" + site + "/memberships?status=PENDING"), manager);
        assertThat((List<String>) JsonPath.read(pending, "$.items[*].comment"))
                .containsExactly("Appartements A-1 et A-2, parking P-1, caves C-1 et C-2");
        String person = login(email);
        call(get("/v1/sites/" + site), person).andExpect(status().isNotFound());
        call(get("/v1/me/memberships"), person).andExpect(jsonPath("$[0].status").value("PENDING"));

        String membership = JsonPath.read(pending, "$.items[0].id");
        call(post("/v1/site-memberships/" + membership + "/approve").content("""
                {"allowedBuildingIds":[],"occupancies":[{"unitId":"%s","role":"OWNER"},{"unitId":"%s","role":"OWNER"},
                 {"unitId":"%s","role":"OWNER"},{"unitId":"%s","role":"OWNER"},{"unitId":"%s","role":"OWNER"}],
                 "version":%d}""".formatted(units.get(0), units.get(1), units.get(2), units.get(3), units.get(4),
                version(pending))), manager)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.allowedBuildingIds[0]").value(building));
        call(get("/v1/me/memberships"), person).andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].occupancies.length()").value(5));
        call(get("/v1/sites/" + site), person).andExpect(status().isOk());
        await(() -> body(get("/v1/notifications"), person).contains("ACCESS_APPROVED"));
    }

    @Test
    void D2_aKnownEmailGetsTheSameAnswerAndNoSecondAccount() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String manager = inviteManager(org, site);
        String code = JsonPath.read(body(get("/v1/sites/" + site + "/registration-qr"), org.owner()), "$.code");
        String email = jdbc.queryForObject("SELECT email FROM identity.person ORDER BY created_at DESC LIMIT 1",
                String.class);

        int before = mail.to(email).size();
        register(code, email, null, null).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CHECK_YOUR_EMAIL"));
        assertThat(mail.awaitAtLeast(email, before + 1)).hasSize(before + 1);
        assertThat(mail.to(email).getLast().text()).doesNotContain("#");
        assertThat(count("SELECT count(*) FROM identity.person WHERE email = '" + email + "'")).isEqualTo(1);
        assertThat(pendingOf(site, manager)).isEmpty();
        register("ZZZZ-ZZZZ-ZZ", newEmail(), null, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGISTRATION_CODE_INVALID"));
    }

    @Test
    void D2_thePublicFormRequiresAnAltchaProof() throws Exception {
        mvc.perform(apiPost("/v1/registrations").content(registrationBody("ABCD-EFGH-JK", newEmail(), null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ALTCHA_INVALID"));
    }

    // ----------------------------------------------------------------------------------- criterion 6, D3

    @Test
    void criterion6_aPreRegisteredPersonIsApprovedWithTheirKeyWhichWorksOnce() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "Bâtiment A");
        String manager = inviteManager(org, site);
        String apartment = createUnit(org.owner(), site, "A-1", "APARTMENT", "T3", building);
        String cellar = createUnit(org.owner(), site, "C-1", "STORAGE", "CELLAR", building);
        String code = JsonPath.read(body(get("/v1/sites/" + site + "/registration-qr"), org.owner()), "$.code");

        String email = newEmail();
        String issued = body(post("/v1/sites/" + site + "/pre-registrations").content("""
                {"person":{"firstName":"Yasmine","lastName":"Attendue","email":"%s","phone":"%s","language":"AR"},
                 "plannedOccupancies":[{"unitId":"%s","role":"OWNER"},{"unitId":"%s","role":"OWNER"}]}"""
                .formatted(email.toUpperCase(), newPhone(), apartment, cellar)), manager);
        String key = JsonPath.read(issued, "$.key");
        String preRegistration = JsonPath.read(issued, "$.preRegistration.id");
        assertThat(body(get("/v1/pre-registrations/" + preRegistration), manager)).doesNotContain(key)
                .contains("\"status\":\"PENDING\"");

        register(code, email, null, key.toLowerCase()).andExpect(status().isAccepted());
        verifyEmail(email);
        await(() -> body(get("/v1/notifications"), manager).contains("ACCESS_AUTO_APPROVED"));
        String person = login(email);
        call(get("/v1/me/memberships"), person).andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].occupancies.length()").value(2));
        call(get("/v1/pre-registrations/" + preRegistration), manager).andExpect(jsonPath("$.status").value("USED"));

        // Single use: someone else typing the same key goes through the manual approval, without being told.
        String other = newEmail();
        register(code, other, null, key).andExpect(status().isAccepted());
        verifyEmail(other);
        await(() -> body(get("/v1/sites/" + site + "/memberships?status=PENDING"), manager).contains("NOT_MATCHED"));
    }

    @Test
    void D3_anExpiredKeyOrAnotherEmailFallsBackToTheManualApproval() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "Bâtiment A");
        String manager = inviteManager(org, site);
        String apartment = createUnit(org.owner(), site, "A-1", "APARTMENT", "T3", building);
        String code = JsonPath.read(body(get("/v1/sites/" + site + "/registration-qr"), org.owner()), "$.code");

        String planned = newEmail();
        String issued = body(post("/v1/sites/" + site + "/pre-registrations").content("""
                {"person":{"firstName":"Amine","lastName":"Prévu","email":"%s","phone":"%s","language":"FR"},
                 "plannedOccupancies":[{"unitId":"%s","role":"OWNER"}],"keyValidDays":1}"""
                .formatted(planned, newPhone(), apartment)), manager);
        String key = JsonPath.read(issued, "$.key");

        String stranger = newEmail();
        register(code, stranger, null, key).andExpect(status().isAccepted());
        verifyEmail(stranger);
        await(() -> body(get("/v1/sites/" + site + "/memberships?status=PENDING"), manager).contains("NOT_MATCHED"));

        jdbc.update("UPDATE membership.pre_registration SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid",
                (String) JsonPath.read(issued, "$.preRegistration.id"));
        register(code, planned, null, key).andExpect(status().isAccepted());
        verifyEmail(planned);
        await(() -> ((List<?>) JsonPath.read(body(get("/v1/sites/" + site
                + "/memberships?status=PENDING"), manager), "$.items[?(@.keyOutcome=='NOT_MATCHED')]")).size() == 2);
        call(get("/v1/pre-registrations/" + JsonPath.read(issued, "$.preRegistration.id")), manager)
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void D3_E4_aUnitCannotBePlannedForTwoOwners() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "Bâtiment A");
        String apartment = createUnit(org.owner(), site, "A-1", "APARTMENT", "T3", building);
        String body = """
                {"person":{"firstName":"Un","lastName":"Propriétaire","email":"%s","phone":"%s","language":"FR"},
                 "plannedOccupancies":[{"unitId":"%s","role":"OWNER"}]}""";
        call(post("/v1/sites/" + site + "/pre-registrations").content(body.formatted(newEmail(), newPhone(),
                apartment)), org.owner()).andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        call(post("/v1/sites/" + site + "/pre-registrations").content(body.formatted(newEmail(), newPhone(),
                apartment)), org.owner()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OCCUPANCY_OWNER_EXISTS"));
    }

    @Test
    void D3_residentsAreImportedAsPreRegistrationsWithTheirKeysShownOnce() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "Bâtiment A");
        String manager = inviteManager(org, site);
        createUnit(org.owner(), site, "A-1", "APARTMENT", "T3", building);
        createUnit(org.owner(), site, "C-1", "STORAGE", "CELLAR", building);
        createUnit(org.owner(), site, "A-2", "APARTMENT", "T3", building);
        byte[] template = mvc.perform(get("/v1/residents/import-template?language=AR")
                        .header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        String first = newEmail();
        String phone = newPhone();
        List<Map<String, String>> rows = List.of(
                person(first, phone, "A-1", "OWNER"),
                person(first, phone, "C-1", "OWNER"),
                person(newEmail(), newPhone(), "A-2", "TENANT"));
        String report = mvc.perform(multipart("/v1/sites/" + site + "/residents/import")
                        .file(new MockMultipartFile("file", "residents.xlsx", XLSX.toString(), fill(template, rows)))
                        .header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.outcome").value("IMPORTED")).andExpect(jsonPath("$.createdCount").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(report, "$.keys[*].key")).hasSize(2);
        call(get("/v1/sites/" + site + "/pre-registrations?status=PENDING"), manager)
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.items[?(@.email=='" + first + "')].plannedOccupancies.length()").value(2));
    }

    // ------------------------------------------------------------------------------------- criterion 2, A6

    @Test
    void criterion2_defaultTypologiesAreOfferedAndTheAdministratorsAddOne() throws Exception {
        String admin = platformAdmin();
        String defaults = body(get("/v1/admin/unit-categories?unitType=APARTMENT"), admin);
        assertThat((List<String>) JsonPath.read(defaults, "$[*].code"))
                .contains("STUDIO", "T1", "T2", "T3", "T4", "T5", "DUPLEX");
        String code = "T" + ThreadLocalRandom.current().nextInt(6, 100_000);
        call(post("/v1/admin/unit-categories").content("""
                {"unitType":"APARTMENT","code":"%s","name":{"fr":"Grand appartement"}}""".formatted(code)), admin)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.scope.type").value("PLATFORM"));
        Organization org = onboard();
        assertThat(body(get("/v1/unit-categories?organizationId=" + org.id() + "&unitType=APARTMENT"), org.owner()))
                .contains("\"" + code + "\"");
        call(get("/v1/admin/unit-categories"), org.owner()).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------------------- A8

    @Test
    void A8_aProvisionalZoneMergedIntoAValidatedOneMovesItsAddressesAndKeepsTheHistory() throws Exception {
        Organization org = onboard();
        String admin = platformAdmin();
        String provisional = JsonPath.read(call(post("/v1/zones/provisional").content("""
                {"organizationId":"%s","parentId":"%s","name":{"fr":"Commune %d"}}"""
                .formatted(org.id(), zone("16"), ThreadLocalRandom.current().nextInt(1_000_000))), org.owner())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String site = createSiteInZone(org, provisional);

        String listed = body(get("/v1/admin/zones?countryCode=DZ&status=PROVISIONAL"), admin);
        assertThat((List<Number>) JsonPath.read(listed, "$.items[?(@.id=='" + provisional + "')].addressCount"))
                .containsExactly(1);
        call(post("/v1/admin/zones/" + provisional + "/merge").content("""
                {"targetZoneId":"%s","version":0}""".formatted(zone("1601"))), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEACTIVATED"))
                .andExpect(jsonPath("$.mergedInto.code").value("1601"));
        call(get("/v1/sites/" + site), org.owner()).andExpect(jsonPath("$.address.zone.code").value("1601"));
        assertThat(count("SELECT count(*) FROM sites.address WHERE owner_id = '" + site + "'")).isEqualTo(2);
        call(post("/v1/admin/zones/" + provisional + "/merge").content("""
                {"targetZoneId":"%s","version":1}""".formatted(zone("1601"))), admin)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ZONE_NOT_PROVISIONAL"));
        call(get("/v1/admin/zones?countryCode=DZ"), org.owner()).andExpect(status().isForbidden());
    }

    @Test
    void A8_criterion3_aProvisionalZoneIsValidatedWithItsFinalCode() throws Exception {
        Organization org = onboard();
        String admin = platformAdmin();
        String provisional = JsonPath.read(call(post("/v1/zones/provisional").content("""
                {"organizationId":"%s","parentId":"%s","name":{"fr":"Commune %d"}}"""
                .formatted(org.id(), zone("16"), ThreadLocalRandom.current().nextInt(1_000_000))), org.owner())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String code = "16" + ThreadLocalRandom.current().nextInt(50, 99) + ThreadLocalRandom.current().nextInt(100);
        call(post("/v1/admin/zones/" + provisional + "/validate").content("""
                {"code":"%s","version":0}""".formatted(code)), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.ancestors[0].code").value("16"));
        await(() -> count("SELECT count(*) FROM audit.audit_entry WHERE action = 'ZONE_VALIDATED' AND subject_id = '"
                + provisional + "'") == 1);
    }

    // ------------------------------------------------------------------------------------------- C12

    @Test
    void C12_anAdministratorSeesAnOrganizationOnlyInASupportSessionAndEveryActionCarriesIt() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String admin = platformAdmin();
        call(get("/v1/sites/" + site), admin).andExpect(status().isNotFound());
        call(post("/v1/admin/support-sessions").content("""
                {"organizationId":"%s","reason":"court"}""".formatted(org.id())), admin)
                .andExpect(status().isBadRequest());

        String session = JsonPath.read(call(post("/v1/admin/support-sessions").content("""
                {"organizationId":"%s","reason":"Ticket de support 4521 : bâtiment manquant"}""".formatted(org.id())),
                admin).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        call(get("/v1/me/access"), admin).andExpect(jsonPath("$.supportSession.id").value(session));
        call(get("/v1/sites/" + site), admin).andExpect(status().isOk());
        String building = createBuilding(admin, site, "Bâtiment ajouté par le support");
        await(() -> count("SELECT count(*) FROM audit.audit_entry WHERE action = 'SUPPORT_SESSION_STARTED' "
                + "AND support_session_id = '" + session + "'") == 1);
        assertThat(building).isNotBlank();

        call(post("/v1/admin/organizations/" + org.id() + "/deactivate").content("{\"reason\":\"Fin de contrat\"}"),
                admin).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SUPPORT_SESSION_OPEN"));
        call(post("/v1/admin/support-sessions/" + session + "/end"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
        call(get("/v1/sites/" + site), admin).andExpect(status().isNotFound());
    }

    @Test
    void C12_aDeactivatedOrganizationDisappearsForItsPeopleUntilReactivated() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String admin = platformAdmin();
        call(get("/v1/admin/organizations?q=Promoteur&active=true"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + org.id() + "')].ownerCount").value(1));
        call(post("/v1/admin/organizations/" + org.id() + "/deactivate").content("{\"reason\":\"Fin de contrat\"}"),
                admin).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        call(get("/v1/sites/" + site), org.owner()).andExpect(status().isNotFound());
        call(post("/v1/admin/organizations/" + org.id() + "/reactivate").content("{\"reason\":\"Contrat renouvelé\"}"),
                admin).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        call(get("/v1/sites/" + site), org.owner()).andExpect(status().isOk());
        call(get("/v1/admin/organizations"), org.owner()).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------------------------ criterion 20, M6

    @Test
    void criterion20_anEnglishTextEditedByAnAdministratorIsServedWithoutDeployment() throws Exception {
        String admin = platformAdmin();
        String before = mvc.perform(get("/api/v1/texts/EN").contextPath("/api")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        mvc.perform(get("/api/v1/texts/EN").contextPath("/api").header(HttpHeaders.IF_NONE_MATCH, before))
                .andExpect(status().isNotModified());

        call(put("/v1/admin/texts/session.greeting/EN").content("{\"text\":\"Welcome back\"}"), admin)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("TEXT_VARIABLES_CHANGED"));
        String text = "Hi {{name}} " + ThreadLocalRandom.current().nextInt(1_000_000);
        call(put("/v1/admin/texts/session.greeting/EN").content("{\"text\":\"" + text + "\"}"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.origin").value("EDITED"));

        mvc.perform(get("/api/v1/texts/en").contextPath("/api").header(HttpHeaders.IF_NONE_MATCH, before))
                .andExpect(status().isOk()).andExpect(jsonPath("$.texts['session.greeting']").value(text));
        call(get("/v1/admin/texts/session.greeting/revisions"), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].newText").value(text))
                .andExpect(jsonPath("$.items[0].actor").value("PERSON"));
        call(put("/v1/admin/texts/session.greeting/EN").content("{\"text\":\"x\"}"), org().owner())
                .andExpect(status().isForbidden());
        await(() -> count("SELECT count(*) FROM audit.audit_entry WHERE action = 'TEXT_UPDATED' "
                + "AND details->>'key' = 'session.greeting'") >= 1);
    }

    // ------------------------------------------------------------------------------------------- helpers

    private record Organization(String id, String owner) {
    }

    /** A promoteur onboarded by our team, signed in. */
    private Organization org() throws Exception {
        return onboard();
    }

    /** A promoteur onboarded by our team, signed in through the "set my password" link of its e-mail. */
    private Organization onboard() throws Exception {
        String admin = platformAdmin();
        String ownerEmail = newEmail();
        String created = call(post("/v1/organizations").content("""
                {"type":"PROPERTY_DEVELOPER","name":"Promoteur %d","countryCode":"DZ",
                 "owner":{"firstName":"Karim","lastName":"Owner","email":"%s","phone":"%s","language":"FR"}}"""
                .formatted(ThreadLocalRandom.current().nextInt(100_000), ownerEmail, newPhone())), admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Organization(JsonPath.read(created, "$.organization.id"), accept(ownerEmail));
    }

    /** A new platform administrator, signed in. */
    private String platformAdmin() throws Exception {
        String email = newEmail();
        bootstrap.ensurePlatformAdmin(email, newPhone(), "Admin", "Test");
        return accept(email);
    }

    /** A residence in the commune with this code. */
    private String createSite(Organization org, String commune) throws Exception {
        return createSiteInZone(org, zone(commune));
    }

    /** A residence whose address is in this zone. */
    private String createSiteInZone(Organization org, String zoneId) throws Exception {
        return JsonPath.read(call(post("/v1/organizations/" + org.id() + "/sites").content("""
                {"type":"RESIDENTIAL","name":"Résidence Les Pins","lifecycleStatus":"OPERATING",
                 "address":{"zoneId":"%s","addressLine":"12 rue des Pins"}}""".formatted(zoneId)), org.owner())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** A building with a copy of the site's address. */
    private String createBuilding(String bearer, String site, String name) throws Exception {
        return JsonPath.read(call(post("/v1/sites/" + site + "/buildings").content(
                "{\"name\":\"" + name + "\",\"buildingUse\":\"RESIDENTIAL\",\"floorCount\":5}"), bearer)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** A unit of this type and platform category in the building. */
    private String createUnit(String bearer, String site, String reference, String type, String category,
                              String building) throws Exception {
        return JsonPath.read(call(post("/v1/sites/" + site + "/units").content("""
                {"type":"%s","categoryId":"%s","reference":"%s","buildingId":"%s","saleable":true,"rentable":true}"""
                .formatted(type, category(type, category), reference, building)), bearer)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** A manager given by the promoteur on the site, signed in. */
    private String inviteManager(Organization org, String site) throws Exception {
        String email = newEmail();
        call(post("/v1/organizations/" + org.id() + "/invitations").content("""
                {"person":{"firstName":"Nadia","lastName":"Manager","email":"%s","phone":"%s","language":"AR"},
                 "roles":[{"roleCode":"SITE_MANAGER","scope":{"type":"SITE","id":"%s"}}]}"""
                .formatted(email, newPhone(), site)), org.owner()).andExpect(status().isCreated());
        return accept(email);
    }

    /** The public registration form with a solved ALTCHA challenge. */
    private ResultActions register(String code, String email, String comment, String key) throws Exception {
        return mvc.perform(apiPost("/v1/registrations").header("X-Altcha", AltchaTestSupport.solve(mvc))
                .content(registrationBody(code, email, comment, key)));
    }

    /** Body of the public registration form. */
    private static String registrationBody(String code, String email, String comment, String key) {
        Map<String, String> fields = new HashMap<>(Map.of("code", code, "firstName", "Samia", "lastName", "Inscrite",
                "email", email, "phone", newPhone(), "language", "FR", "password", PASSWORD));
        if (comment != null) {
            fields.put("comment", comment);
        }
        if (key != null) {
            fields.put("preRegistrationKey", key);
        }
        return json(fields);
    }

    /** Uses the verification link received by this e-mail. */
    private void verifyEmail(String email) throws Exception {
        String token = mail.lastSecret(email, "verify-email").orElseThrow();
        mvc.perform(apiPost("/v1/accounts/verify-email").content(json("token", token)))
                .andExpect(status().is2xxSuccessful());
    }

    /** Signs in and returns the {@code Authorization} header. */
    private String login(String email) throws Exception {
        String session = mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(session, "$.accessToken");
    }

    /** Uses the "set my password" link received by this e-mail and returns the {@code Authorization} header. */
    private String accept(String email) throws Exception {
        String token = mail.lastSecret(email, "invitation").orElseThrow();
        String session = mvc.perform(apiPost("/v1/invitations/accept")
                        .content(json("token", token, "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(session, "$.accessToken");
    }

    /** The public preview of a registration code. */
    private ResultActions inspect(String code) throws Exception {
        return mvc.perform(apiPost("/v1/registration-qr/inspect").content(json("code", code)));
    }

    /** The submitted pending requests of a site, as the manager sees them. */
    private List<String> pendingOf(String site, String manager) {
        return JsonPath.read(body(get("/v1/sites/" + site + "/memberships?status=PENDING"), manager), "$.items[*].id");
    }

    /** The version of the first access of a page. */
    private static long version(String page) {
        Number version = JsonPath.read(page, "$.items[0].version");
        return version.longValue();
    }

    /** One row of the resident import. */
    private static Map<String, String> person(String email, String phone, String unit, String role) {
        return Map.of("lastName", "Importé", "firstName", "Rachid", "email", email, "phone", phone, "language", "FR",
                "unit", unit, "role", role);
    }

    /** Uploads a unit import file. */
    private ResultActions upload(String site, byte[] file, boolean dryRun, String bearer) throws Exception {
        return mvc.perform(multipart("/v1/sites/" + site + "/units/import" + (dryRun ? "?dryRun=true" : ""))
                .file(new MockMultipartFile("file", "units.xlsx", XLSX.toString(), file))
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /** Fills the rows of a template below its labels, matching the hidden key row, as a person would in Excel. */
    private static byte[] fill(byte[] template, List<Map<String, String>> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            Row keys = sheet.getRow(0);
            Map<String, Integer> columns = new HashMap<>();
            for (int c = 0; c < keys.getLastCellNum(); c++) {
                columns.put(keys.getCell(c).getStringCellValue(), c);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 2);
                rows.get(r).forEach((key, value) -> row.createCell(columns.get(key)).setCellValue(value));
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Number of units of a site. */
    private long units(String site) {
        return count("SELECT count(*) FROM sites.unit WHERE site_id = '" + site + "'");
    }

    /** A count query (test data only, values generated by the test). */
    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    /** Identifier of a zone of the development reference data, by code. */
    private String zone(String code) {
        return jdbc.queryForObject("SELECT id::text FROM sites.zone WHERE country_code = 'DZ' AND code = ?",
                String.class, code);
    }

    /** Identifier of a platform default category. */
    private String category(String unitType, String code) {
        return jdbc.queryForObject("""
                SELECT id::text FROM sites.unit_category WHERE scope_type = 'PLATFORM' AND unit_type = ? AND code = ?""",
                String.class, unitType, code);
    }

    /** Calls the API with an access token. */
    private ResultActions call(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /** The body of a call. */
    private String body(MockHttpServletRequestBuilder request, String bearer) {
        try {
            return call(request, bearer).andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A POST under the /api context path, as the browser sends it (ALTCHA and rate limits apply there). */
    private static MockHttpServletRequestBuilder apiPost(String path) {
        return post("/api" + path).contextPath("/api").contentType(MediaType.APPLICATION_JSON);
    }

    /** A JSON object from key and value pairs. */
    private static String json(String... pairs) {
        Map<String, String> fields = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            fields.put(pairs[i], pairs[i + 1]);
        }
        return json(fields);
    }

    /** A JSON object of strings. */
    private static String json(Map<String, String> fields) {
        StringBuilder out = new StringBuilder("{");
        fields.forEach((k, v) -> out.append(out.length() > 1 ? "," : "").append('"').append(k).append("\":\"")
                .append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append('"'));
        return out.append('}').toString();
    }

    /** A new e-mail address. */
    private static String newEmail() {
        return "s2-" + UUID.randomUUID().toString().substring(0, 8) + "@example.dz";
    }

    /** A real Algerian mobile range (ADR-028). */
    private static String newPhone() {
        return "+21366" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    /** Waits for an eventually consistent condition (notifications, audit, asynchronous submission). */
    private static void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition not met within 10 s");
            }
            Thread.sleep(150);
        }
    }
}
