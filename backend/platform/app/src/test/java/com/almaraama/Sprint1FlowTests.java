package com.almaraama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.almaraama.membership.application.PlatformBootstrap;
import com.almaraama.membership.application.RoleCatalog;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Sprint 1 journeys of spec v3.4 through the HTTP API on a real database: residence in a commune with buildings whose
 * address is copied or their own (criteria 1 without import, 18; A1, A8, A9), unit categories (criterion 2, A6),
 * filters on the zone tree and provisional zones (criterion 3), accounts and accesses (D2, D4, C7), one owner per unit
 * (criterion 15, E4), isolation (criterion 13), and permissions as data with differences per organization and
 * adjustments per person (criteria 16 and 17, C8 to C11, ADR-026 revision 2). E-mails are recorded, never sent.
 */
@SpringBootTest(properties = "almaraama.security.auth-requests-per-minute=10000")
@AutoConfigureMockMvc
class Sprint1FlowTests extends PostgresIntegrationTest {

    private static final String PASSWORD = "Une résidence bien tenue";

    @Autowired
    MockMvc mvc;

    @Autowired
    PlatformBootstrap bootstrap;

    @Autowired
    RecordingEmailSender mail;

    @Autowired
    RoleCatalog roleCatalog;

    @Autowired
    JdbcTemplate jdbc;

    // --------------------------------------------------------------------- criteria 1 and 18, A1, A8, A9

    @Test
    void criterion18_aBuildingGetsACopyOfTheResidenceAddressOrItsOwn() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");

        call(get("/v1/sites/" + site), org.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode").value("DZ"))
                .andExpect(jsonPath("$.address.zone.code").value("1601"))
                .andExpect(jsonPath("$.address.zone.ancestors[0].code").value("16"))
                .andExpect(jsonPath("$.address.addressLine").value("12 rue des Pins"));

        String copied = createBuilding(org.owner(), site, "Bâtiment A", null);
        String own = createBuilding(org.owner(), site, "Bâtiment B",
                "{\"zoneId\":\"%s\",\"addressLine\":\"3 rue Didouche\",\"postalCode\":\"16000\"}"
                        .formatted(zone("1602")));
        String buildings = body(get("/v1/sites/" + site + "/buildings"), org.owner());
        assertThat((List<String>) JsonPath.read(buildings, "$[?(@.id=='" + copied + "')].addressOrigin"))
                .containsExactly("SITE_COPY");
        assertThat((List<String>) JsonPath.read(buildings, "$[?(@.id=='" + own + "')].address.postalCode"))
                .containsExactly("16000");

        // No live inheritance (decision of 20 November): the residence moves, its buildings keep their address.
        call(put("/v1/sites/" + site).content("""
                {"name":"Résidence Les Pins","lifecycleStatus":"OPERATING","active":true,"version":0,
                 "address":{"zoneId":"%s","addressLine":"1 boulevard nouveau"}}""".formatted(zone("1621"))),
                org.owner()).andExpect(status().isOk());
        assertThat((List<String>) JsonPath.read(body(get("/v1/sites/" + site + "/buildings"), org.owner()),
                "$[?(@.id=='" + copied + "')].address.addressLine")).containsExactly("12 rue des Pins");
    }

    @Test
    void A9_anAddressIsAttachedToACommuneOfTheOrganizationsCountry() throws Exception {
        Organization org = onboard();
        call(post("/v1/organizations/" + org.id() + "/sites").content(siteBody(zone("16"))), org.owner())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ZONE_NOT_ATTACHMENT_LEVEL"));
        call(post("/v1/organizations/" + org.id() + "/sites").content(siteBody(UUID.randomUUID().toString())),
                org.owner()).andExpect(status().isNotFound());
    }

    @Test
    void M5_theOrganizationCarriesItsCountryAndCurrency() throws Exception {
        Organization org = onboard();
        call(get("/v1/organizations/" + org.id()), org.owner()).andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode").value("DZ"))
                .andExpect(jsonPath("$.currencyCode").value("DZD"));
    }

    @Test
    void A1_aResidenceClientWithoutPromoteurHasASingleSite() throws Exception {
        Organization org = onboard("RESIDENCE");
        createSite(org, "1601");
        call(post("/v1/organizations/" + org.id() + "/sites").content(siteBody(zone("1601"))), org.owner())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_SINGLE_SITE"));
    }

    // --------------------------------------------------------------------------------- criterion 3, A8

    @Test
    void criterion3_sitesAreFilteredAtEveryLevelAndAProvisionalZoneCanBeUsed() throws Exception {
        Organization org = onboard();
        String algiers = createSite(org, "1601");
        String oran = createSite(org, "3101");

        String byWilaya = body(get("/v1/organizations/" + org.id() + "/sites?zoneId=" + zone("16")), org.owner());
        assertThat((List<String>) JsonPath.read(byWilaya, "$.items[*].id")).containsExactly(algiers);
        String byCommune = body(get("/v1/organizations/" + org.id() + "/sites?zoneId=" + zone("3101")),
                org.owner());
        assertThat((List<String>) JsonPath.read(byCommune, "$.items[*].id")).containsExactly(oran);

        call(get("/v1/reference/countries/DZ/zones?parentId=" + zone("16")), org.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.code=='1601')].ancestors[0].code").value("16"));

        String provisional = JsonPath.read(call(post("/v1/zones/provisional").content("""
                {"organizationId":"%s","parentId":"%s","name":{"fr":"Nouvelle commune %d"}}"""
                .formatted(org.id(), zone("16"), ThreadLocalRandom.current().nextInt(1_000_000))), org.owner())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROVISIONAL"))
                .andExpect(jsonPath("$.level").value(2))
                .andReturn().getResponse().getContentAsString(), "$.id");
        call(post("/v1/organizations/" + org.id() + "/sites").content(siteBody(provisional)), org.owner())
                .andExpect(status().isCreated());
        assertThat((List<String>) JsonPath.read(body(get("/v1/organizations/" + org.id() + "/sites?zoneId="
                + zone("16")), org.owner()), "$.items[*].id")).hasSize(2);

        Organization other = onboard();
        call(get("/v1/reference/zones/" + provisional), other.owner()).andExpect(status().isNotFound());
        call(post("/v1/organizations/" + other.id() + "/sites").content(siteBody(provisional)), other.owner())
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------ criterion 2, A6, A7

    @Test
    void criterion2_defaultTypologiesAreOfferedAndThePromoteurAndManagerAddCategories() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String manager = inviteManager(org, site);

        assertThat((List<String>) JsonPath.read(body(get("/v1/unit-categories?siteId=" + site
                + "&unitType=APARTMENT"), manager), "$[*].code"))
                .contains("STUDIO", "T1", "T2", "T3", "T4", "T5", "DUPLEX");

        call(post("/v1/unit-categories").content("""
                {"scope":{"type":"ORGANIZATION","id":"%s"},"unitType":"SHOP","code":"KIOSQUE",
                 "name":{"fr":"Kiosque"}}""".formatted(org.id())), org.owner())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name.ar").value("Kiosque"));
        call(post("/v1/unit-categories").content("""
                {"scope":{"type":"SITE","id":"%s"},"unitType":"PARKING","code":"BOX","name":{"fr":"Box fermé"}}"""
                .formatted(site)), manager).andExpect(status().isCreated());
        call(post("/v1/unit-categories").content("""
                {"scope":{"type":"ORGANIZATION","id":"%s"},"unitType":"SHOP","code":"KIOSQUE2",
                 "name":{"fr":"Kiosque"}}""".formatted(org.id())), manager).andExpect(status().isForbidden());
        assertThat((List<String>) JsonPath.read(body(get("/v1/unit-categories?siteId=" + site), manager),
                "$[*].code")).contains("KIOSQUE", "BOX");
    }

    @Test
    void A6_aUnitHasACategoryOfItsTypeAndIsInOnePlace() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "A", null);
        String area = JsonPath.read(call(post("/v1/sites/" + site + "/outdoor-areas")
                .content("{\"type\":\"PARKING\",\"name\":\"Parking nord\"}"), org.owner())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        call(post("/v1/sites/" + site + "/units").content("""
                {"type":"APARTMENT","reference":"X1","buildingId":"%s","saleable":true,"rentable":true}"""
                .formatted(building)), org.owner())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        call(post("/v1/sites/" + site + "/units").content(unit("X1", building, category("PARKING", "COVERED"))),
                org.owner())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNIT_CATEGORY_TYPE_MISMATCH"));
        call(post("/v1/sites/" + site + "/units").content("""
                {"type":"PARKING","categoryId":"%s","reference":"P1","buildingId":"%s","outdoorAreaId":"%s",
                 "saleable":true,"rentable":true}""".formatted(category("PARKING", "COVERED"), building, area)),
                org.owner())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNIT_LOCATION_INVALID"));
        call(post("/v1/sites/" + site + "/units").content("""
                {"type":"OTHER","categoryId":"%s","reference":"LOCAL-1","outdoorAreaId":"%s",
                 "saleable":false,"rentable":true}""".formatted(category("OTHER", "UNCLASSIFIED"), area)),
                org.owner())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.category.code").value("UNCLASSIFIED"));

        createUnit(org.owner(), site, "A-01", building);
        call(post("/v1/sites/" + site + "/units").content(unit("a-01", building, category("APARTMENT", "T3"))),
                org.owner()).andExpect(status().isConflict()).andExpect(jsonPath("$.code")
                .value("UNIT_REFERENCE_TAKEN"));
    }

    // ------------------------------------------------------------------------------------------- D4, E1

    @Test
    void D4_aManagerCreatesAResidentWhoSetsTheirPasswordAndSeesTheirResidence() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "A", null);
        String unit = createUnit(org.owner(), site, "A-12", building);
        String manager = inviteManager(org, site);

        String resident = inviteResident(manager, site, unit, "OWNER");
        await(() -> body(get("/v1/me/access"), resident).contains(unit));
        call(get("/v1/me/access"), resident).andExpect(status().isOk())
                .andExpect(jsonPath("$.sites[0].id").value(site))
                .andExpect(jsonPath("$.sites[0].roles[0]").value("RESIDENT"))
                .andExpect(jsonPath("$.sites[0].permissions.length()").value(3));
        call(get("/v1/me/memberships"), resident).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].occupancies[0].unitReference").value("A-12"));
        call(get("/v1/units/" + unit), resident).andExpect(status().isOk());

        call(post("/v1/sites/" + site + "/units").content(unit("X-1", building, category("APARTMENT", "T2"))),
                resident).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        call(get("/v1/sites/" + site + "/memberships"), resident).andExpect(status().isForbidden());
    }

    @Test
    void D5_theManagerSendsANewPasswordLinkToAResidentOnly() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String unit = createUnit(org.owner(), site, "A-1", createBuilding(org.owner(), site, "A", null));
        String manager = inviteManager(org, site);
        String email = newEmail();
        inviteResident(manager, site, unit, "OWNER", email);
        String residentId = jdbc.queryForObject("SELECT id::text FROM identity.person WHERE email = ?",
                String.class, email);
        String ownerId = JsonPath.read(body(get("/v1/me"), org.owner()), "$.id");

        call(post("/v1/organizations/" + org.id() + "/people/" + residentId + "/password-link"), manager)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.kind").value("PASSWORD_RESET"));
        assertThat(mail.lastSecret(email, "reset-password")).isPresent();
        call(post("/v1/organizations/" + org.id() + "/people/" + ownerId + "/password-link"), manager)
                .andExpect(status().isForbidden());
    }

    @Test
    void criterion15_E4_aUnitRefusesASecondActiveOwner() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String unit = createUnit(org.owner(), site, "A-1", createBuilding(org.owner(), site, "A", null));
        String manager = inviteManager(org, site);
        inviteResident(manager, site, unit, "OWNER");

        call(post("/v1/sites/" + site + "/invitations").content(siteInvitation(newEmail(), unit, "OWNER")), manager)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OCCUPANCY_OWNER_EXISTS"));
    }

    // --------------------------------------------------------------------------------------- D2, C7, K1

    @Test
    void D2_C7_anAccessRequestIsApprovedForTheChosenBuildingsOnly() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String a = createBuilding(org.owner(), site, "A", null);
        String b = createBuilding(org.owner(), site, "B", null);
        String manager = inviteManager(org, site);
        String someone = activePersonElsewhere();

        String code = JsonPath.read(body(get("/v1/sites/" + site + "/registration-qr"), manager), "$.code");
        call(post("/v1/sites/" + site + "/registration-qr/rotate"), manager).andExpect(status().isForbidden());
        mvc.perform(json(post("/v1/registration-qr/inspect")).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.siteName").value("Résidence Les Pins"))
                .andExpect(jsonPath("$.buildings").doesNotExist());

        call(post("/v1/me/access-requests").content("""
                {"code":"%s","comment":"Appartement A-7, locataire"}""".formatted(code)), someone)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        await(() -> body(get("/v1/notifications/unread-count"), manager).contains("\"count\":1"));
        String pending = body(get("/v1/sites/" + site + "/memberships?status=PENDING"), manager);
        assertThat((String) JsonPath.read(pending, "$.items[0].comment")).isEqualTo("Appartement A-7, locataire");
        String membership = JsonPath.read(pending, "$.items[0].id");

        call(post("/v1/site-memberships/" + membership + "/approve").content("""
                {"allowedBuildingIds":["%s"],"occupancies":[],"version":0}""".formatted(a)), manager)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        call(get("/v1/sites/" + site + "/buildings"), someone).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(a));
        assertThat(body(get("/v1/sites/" + site + "/buildings"), someone)).doesNotContain(b);
        await(() -> body(get("/v1/notifications"), someone).contains("ACCESS_APPROVED"));
    }

    // ----------------------------------------------------------------------------------- criterion 13, M3

    @Test
    void criterion13_anOrganizationSeesNothingOfAnother() throws Exception {
        Organization first = onboard();
        String site = createSite(first, "1601");
        String manager = inviteManager(first, site);
        Organization second = onboard();

        call(get("/v1/organizations/" + first.id()), second.owner()).andExpect(status().isNotFound());
        call(get("/v1/sites/" + site), second.owner()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        call(get("/v1/sites/" + site + "/units"), second.owner()).andExpect(status().isNotFound());
        call(get("/v1/organizations/" + second.id() + "/sites"), manager).andExpect(status().isNotFound());
        call(get("/v1/unit-categories?organizationId=" + first.id()), second.owner()).andExpect(status().isNotFound());
        assertThat(body(get("/v1/organizations"), second.owner())).doesNotContain(first.id());
    }

    // -------------------------------------------------------------------- permissions (ADR-026 revision 2)

    @Test
    void criterion16_C9_C11_thePromoteurChangesARoleAndOnePersonThenGoesBackToDefaults() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "A", null);
        String manager = inviteManager(org, site);
        String managerId = JsonPath.read(body(get("/v1/me"), manager), "$.id");

        // Defaults (section 3.3): a manager creates units but not buildings.
        createUnit(manager, site, "M-1", building);
        call(post("/v1/sites/" + site + "/buildings").content(building("B")), manager)
                .andExpect(status().isForbidden());

        // For the whole organization: managers no longer create units. Only the difference is stored (C11).
        List<String> wanted = ((List<String>) JsonPath.read(body(get("/v1/roles"), org.owner()),
                "$[?(@.code=='SITE_MANAGER')].defaultPermissions[*]")).stream()
                .filter(p -> !p.equals("unit:create")).toList();
        call(put("/v1/organizations/" + org.id() + "/roles/SITE_MANAGER").content(
                "{\"permissions\":" + jsonArray(wanted) + "}"), org.owner())
                .andExpect(status().isOk()).andExpect(jsonPath("$.customized").value(true))
                .andExpect(jsonPath("$.deltas.length()").value(1))
                .andExpect(jsonPath("$.deltas[0].effect").value("REMOVE"))
                .andExpect(jsonPath("$.deltas[0].permission").value("unit:create"));
        call(post("/v1/sites/" + site + "/units").content(unit("M-2", building, category("APARTMENT", "T2"))),
                manager).andExpect(status().isForbidden());

        // For this person only: may create buildings.
        String adjustment = JsonPath.read(call(post("/v1/organizations/" + org.id() + "/permission-adjustments")
                .content("""
                        {"personId":"%s","scope":{"type":"SITE","id":"%s"},"permission":"building:create",
                         "effect":"ADD"}""".formatted(managerId, site)), org.owner())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        call(post("/v1/sites/" + site + "/buildings").content(building("B")), manager)
                .andExpect(status().isCreated());
        call(get("/v1/organizations/" + org.id() + "/people/" + managerId + "/permissions"), org.owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[0].permissions[?(@.permission=='building:create')].origin")
                        .value("PERSON_ADDED"))
                .andExpect(jsonPath("$.scopes[0].removed[?(@.permission=='unit:create')].origin")
                        .value("ROLE_DELTA"));

        // Cancelled: the role's permissions apply again, on the next request (C10).
        call(post("/v1/permission-adjustments/" + adjustment + "/cancel"), org.owner()).andExpect(status().isOk());
        call(post("/v1/sites/" + site + "/buildings").content(building("C")), manager)
                .andExpect(status().isForbidden());

        // Back to the defaults (section 3.6).
        call(post("/v1/organizations/" + org.id() + "/roles/SITE_MANAGER/reset"), org.owner())
                .andExpect(status().isOk()).andExpect(jsonPath("$.customized").value(false));
        createUnit(manager, site, "M-3", building);

        await(() -> jdbc.queryForObject("select count(*) from audit.audit_entry where action ="
                + " 'ROLE_PERMISSIONS_CHANGED' and subject_id = ?::uuid", Long.class, org.id()) == 2);
    }

    @Test
    void criterion17_C11_aDefaultChangedInTheDatabaseChangesBehaviourAndKeepsTheDifferences() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String building = createBuilding(org.owner(), site, "A", null);
        String manager = inviteManager(org, site);
        List<String> withoutImport = ((List<String>) JsonPath.read(body(get("/v1/roles"), org.owner()),
                "$[?(@.code=='SITE_MANAGER')].defaultPermissions[*]")).stream()
                .filter(p -> !p.equals("unit:import")).toList();
        call(put("/v1/organizations/" + org.id() + "/roles/SITE_MANAGER").content(
                "{\"permissions\":" + jsonArray(withoutImport) + "}"), org.owner()).andExpect(status().isOk());
        call(put("/v1/buildings/" + building).content(buildingUpdate("A bis", building)), manager)
                .andExpect(status().isForbidden());

        // A later phase adds building:update to the manager's defaults, by data only (RB-6).
        superuser("INSERT INTO membership.role_default_permission (role_code, permission)"
                + " VALUES ('SITE_MANAGER', 'building:update')");
        try {
            roleCatalog.refresh();
            call(put("/v1/buildings/" + building).content(buildingUpdate("A bis", building)), manager)
                    .andExpect(status().isOk());
            String effective = body(get("/v1/organizations/" + org.id() + "/roles"), org.owner());
            List<String> managerPermissions = JsonPath.read(effective,
                    "$[?(@.roleCode=='SITE_MANAGER')].effectivePermissions[*]");
            assertThat(managerPermissions).contains("building:update").doesNotContain("unit:import");
        } finally {
            superuser("DELETE FROM membership.role_default_permission"
                    + " WHERE role_code = 'SITE_MANAGER' AND permission = 'building:update'");
            roleCatalog.refresh();
        }
    }

    @Test
    void C8_C9_onlyThePromoteurChangesPermissionsAndTheLastPromoteurStays() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String manager = inviteManager(org, site);
        String managerId = JsonPath.read(body(get("/v1/me"), manager), "$.id");

        call(put("/v1/organizations/" + org.id() + "/roles/SITE_MANAGER").content(
                "{\"permissions\":[\"site:create\"]}"), manager).andExpect(status().isForbidden());
        call(put("/v1/organizations/" + org.id() + "/roles/ORG_ADMIN").content("{\"permissions\":[]}"), org.owner())
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ROLE_NOT_EDITABLE"));
        call(put("/v1/organizations/" + org.id() + "/roles/SITE_MANAGER").content(
                "{\"permissions\":[\"permission-adjustment:manage\"]}"), org.owner())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_ADJUSTABLE"));
        call(post("/v1/organizations/" + org.id() + "/permission-adjustments").content("""
                {"personId":"%s","scope":{"type":"SITE","id":"%s"},"permission":"role-assignment:grant",
                 "effect":"ADD"}""".formatted(managerId, site)), org.owner())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_ADJUSTABLE"));

        // C9: a manager gives no role but the resident's.
        call(post("/v1/sites/" + site + "/invitations").content("""
                {"person":{"firstName":"Rania","lastName":"Staff","email":"%s","phone":"%s","language":"AR"},
                 "roleCode":"SITE_MANAGER"}""".formatted(newEmail(), newPhone())), manager)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_NOT_GRANTABLE"));

        String ownerId = JsonPath.read(body(get("/v1/me"), org.owner()), "$.id");
        String assignments = body(get("/v1/organizations/" + org.id() + "/role-assignments?roleCode=ORG_ADMIN"),
                org.owner());
        List<String> ids = JsonPath.read(assignments, "$.items[?(@.person.id=='" + ownerId + "')].id");
        call(post("/v1/role-assignments/" + ids.get(0) + "/end").content("{}"), org.owner())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LAST_ORGANIZATION_ADMIN"));
    }

    @Test
    void C2_C3_aTechnicianIsGivenOnAResidenceNeverOnTheOrganization() throws Exception {
        Organization org = onboard();
        String site = createSite(org, "1601");
        String person = """
                {"person":{"firstName":"Omar","lastName":"Tech","email":"%s","phone":"%s","language":"FR"},
                 "roles":[{"roleCode":"TECHNICIAN","scope":{"type":"%s","id":"%s"}}]}""";
        call(post("/v1/organizations/" + org.id() + "/invitations").content(
                person.formatted(newEmail(), newPhone(), "SITE", site)), org.owner())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.outcome").value("INVITED"));
        call(post("/v1/organizations/" + org.id() + "/invitations").content(
                person.formatted(newEmail(), newPhone(), "ORGANIZATION", org.id())), org.owner())
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ROLE_SCOPE_INVALID"));
    }

    // ------------------------------------------------------------------------------------------- helpers

    private record Organization(String id, String owner) {
    }

    /** A promoteur onboarded by our team, signed in through the "set my password" link of its e-mail. */
    private Organization onboard() throws Exception {
        return onboard("PROPERTY_DEVELOPER");
    }

    /** An organization of this type onboarded by our team, with its first promoteur signed in. */
    private Organization onboard(String type) throws Exception {
        String adminEmail = newEmail();
        bootstrap.ensurePlatformAdmin(adminEmail, newPhone(), "Admin", "Test");
        String admin = accept(adminEmail);
        String ownerEmail = newEmail();
        String created = call(post("/v1/organizations").content("""
                {"type":"%s","name":"Promoteur %d","countryCode":"DZ",
                 "owner":{"firstName":"Karim","lastName":"Owner","email":"%s","phone":"%s","language":"FR"}}"""
                .formatted(type, ThreadLocalRandom.current().nextInt(100_000), ownerEmail, newPhone())), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner.outcome").value("INVITED"))
                .andExpect(jsonPath("$.owner.invitationUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return new Organization(JsonPath.read(created, "$.organization.id"), accept(ownerEmail));
    }

    /** A residence in the commune with this code. */
    private String createSite(Organization org, String commune) throws Exception {
        return JsonPath.read(call(post("/v1/organizations/" + org.id() + "/sites").content(siteBody(zone(commune))),
                org.owner()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Body of a residence whose address is in this zone. */
    private static String siteBody(String zoneId) {
        return """
                {"type":"RESIDENTIAL","name":"Résidence Les Pins","lifecycleStatus":"OPERATING",
                 "address":{"zoneId":"%s","addressLine":"12 rue des Pins"}}""".formatted(zoneId);
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

    /** A building, with a copy of the site's address when {@code address} is null. */
    private String createBuilding(String bearer, String site, String name, String address) throws Exception {
        String body = address == null ? building(name)
                : "{\"name\":\"" + name + "\",\"buildingUse\":\"RESIDENTIAL\",\"floorCount\":5,\"address\":"
                + address + "}";
        return JsonPath.read(call(post("/v1/sites/" + site + "/buildings").content(body), bearer)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** A T3 apartment on the second floor of the building. */
    private String createUnit(String bearer, String site, String reference, String building) throws Exception {
        return JsonPath.read(call(post("/v1/sites/" + site + "/units").content(unit(reference, building,
                        category("APARTMENT", "T3"))), bearer)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** A manager given by the promoteur on the site (C2), signed in. */
    private String inviteManager(Organization org, String site) throws Exception {
        String email = newEmail();
        call(post("/v1/organizations/" + org.id() + "/invitations").content("""
                {"person":{"firstName":"Nadia","lastName":"Manager","email":"%s","phone":"%s","language":"AR"},
                 "roles":[{"roleCode":"SITE_MANAGER","scope":{"type":"SITE","id":"%s"}}]}"""
                .formatted(email, newPhone(), site)), org.owner()).andExpect(status().isCreated());
        return accept(email);
    }

    /** A resident created by the manager with a unit, signed in. */
    private String inviteResident(String manager, String site, String unit, String role) throws Exception {
        return inviteResident(manager, site, unit, role, newEmail());
    }

    /** A resident with this e-mail created by the manager with a unit, signed in. */
    private String inviteResident(String manager, String site, String unit, String role, String email)
            throws Exception {
        call(post("/v1/sites/" + site + "/invitations").content(siteInvitation(email, unit, role)), manager)
                .andExpect(status().isCreated());
        return accept(email);
    }

    /** Someone with an active account in another organization, signed in. */
    private String activePersonElsewhere() throws Exception {
        Organization elsewhere = onboard();
        return elsewhere.owner();
    }

    /** Body of a site invitation with one unit. */
    private static String siteInvitation(String email, String unit, String role) {
        return """
                {"person":{"firstName":"Samir","lastName":"Resident","email":"%s","phone":"%s","language":"FR"},
                 "occupancies":[{"unitId":"%s","role":"%s"}]}""".formatted(email, newPhone(), unit, role);
    }

    /** Body of a building without address. */
    private static String building(String name) {
        return "{\"name\":\"" + name + "\",\"buildingUse\":\"RESIDENTIAL\",\"floorCount\":5}";
    }

    /** Body of a building update that keeps the current address. */
    private String buildingUpdate(String name, String building) {
        String zoneId = jdbc.queryForObject("""
                SELECT zone_id::text FROM sites.address WHERE owner_type = 'BUILDING' AND owner_id = ?::uuid
                  AND replaced_at IS NULL""", String.class, building);
        long version = jdbc.queryForObject("SELECT version FROM sites.building WHERE id = ?::uuid", Long.class,
                building);
        return """
                {"name":"%s","buildingUse":"RESIDENTIAL","floorCount":5,"active":true,"version":%d,
                 "address":{"zoneId":"%s","addressLine":"12 rue des Pins"}}""".formatted(name, version, zoneId);
    }

    /** Body of an apartment of this category on the second floor of the building. */
    private static String unit(String reference, String building, String category) {
        return """
                {"type":"APARTMENT","categoryId":"%s","reference":"%s","buildingId":"%s","floor":2,
                 "saleable":true,"rentable":true}""".formatted(category, reference, building);
    }

    /** Uses the "set my password" link received by this e-mail and returns the {@code Authorization} header. */
    private String accept(String email) throws Exception {
        String token = mail.lastSecret(email, "invitation").orElseThrow();
        String session = mvc.perform(json(post("/v1/invitations/accept"))
                        .content("{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(session, "$.accessToken");
    }

    /** Runs a statement as the database superuser (role tables the application may not write, criterion 17). */
    private static void superuser(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    /** Calls the API with an access token. */
    private ResultActions call(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(json(request).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /** The body of a call. */
    private String body(MockHttpServletRequestBuilder request, String bearer) {
        try {
            return call(request, bearer).andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** JSON request and answer. */
    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
    }

    /** A JSON array of strings. */
    private static String jsonArray(List<String> values) {
        return values.stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    /** A new e-mail address. */
    private static String newEmail() {
        return "s1-" + UUID.randomUUID().toString().substring(0, 8) + "@example.dz";
    }

    /** A real Algerian mobile range (ADR-028). */
    private static String newPhone() {
        return "+21366" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    /** Waits for an eventually consistent condition (notifications, audit). */
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
