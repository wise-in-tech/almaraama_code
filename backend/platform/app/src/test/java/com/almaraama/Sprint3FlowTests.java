package com.almaraama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.almaraama.maintenance.adapters.in.scheduling.TicketAutoClose;
import com.almaraama.membership.application.PlatformBootstrap;
import com.jayway.jsonpath.JsonPath;
import java.awt.Color;
import java.awt.image.BufferedImage;
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
import javax.imageio.ImageIO;
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
 * Sprint 3 journeys of spec v3.4 through the HTTP API on a real database: equipment and their QR code (G1 to G4,
 * criterion 8), files checked by content and read through signed links (H10, ADR-038), routing by type (H4, criterion
 * 9), visibility by relationships (H8, C3, C4, criteria 10 and 13), the circuit with its reasons, cancellations and
 * separation of duties (H5, H6, H7, C6), the options of each client (H5, criterion 19), in-app notifications (K1, K3,
 * criterion 21), the equipment import (ADR-027 revision 3) and the publication check of a circuit (ADR-036).
 */
@SpringBootTest(properties = "almaraama.security.auth-requests-per-minute=10000")
@AutoConfigureMockMvc
class Sprint3FlowTests extends PostgresIntegrationTest {

    private static final String PASSWORD = "Une résidence bien tenue";
    private static final String ELEVATOR = "0194b000-0000-7000-8000-000000000001";
    private static final String ELEVATOR_STUCK = "0194b000-0000-7000-8000-000000000051";
    private static final String WATER_LEAK = "0194b000-0000-7000-8000-000000000052";
    private static final String DIRTY_STAIRS = "0194b000-0000-7000-8000-000000000053";
    private static final String NOISE = "0194b000-0000-7000-8000-000000000054";
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

    @Autowired
    TicketAutoClose autoClose;

    // ---------------------------------------------------------------------------------------- criterion 8

    @Test
    void criterion8_aResidentReportsByTheQrCodeOfAnEquipmentInThreeScreens() throws Exception {
        Residence r = residence();
        String asset = body(post("/v1/sites/" + r.site() + "/assets").content("""
                {"familyId":"%s","name":"Ascenseur A","buildingId":"%s",
                 "attributes":{"capacityKg":630,"doorType":"AUTOMATIC"}}""".formatted(ELEVATOR, r.building())),
                r.manager());
        assertThat((String) JsonPath.read(asset, "$.reference")).isEqualTo("ELEVATOR-001");
        assertThat((Integer) JsonPath.read(asset, "$.management.attributes.capacityKg")).isEqualTo(630);
        String qr = JsonPath.read(asset, "$.qrCode");

        // Screen 0: the label. A resident of the residence opens it; someone of another promoteur gets 404.
        call(get("/v1/asset-qr/" + qr), r.resident()).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ascenseur A")).andExpect(jsonPath("$.qrCode").doesNotExist());
        call(get("/v1/asset-qr/" + qr), onboard().owner()).andExpect(status().isNotFound());
        call(get("/v1/assets/" + JsonPath.read(asset, "$.id")), r.resident()).andExpect(status().isOk())
                .andExpect(jsonPath("$.management").doesNotExist());

        // Screen 1: what is wrong, filtered by the family of the equipment.
        String catalog = body(get("/v1/sites/" + r.site() + "/issue-catalog?assetId=" + JsonPath.read(asset, "$.id")),
                r.resident());
        assertThat((List<String>) JsonPath.read(catalog, "$[*].types[*].code")).contains("ELEVATOR_STUCK");

        // Screen 2: details and a photo, shrunk then re-encoded by the server; then the ticket.
        String photo = upload(png(), "photo.png", "TICKET_PHOTO", r.resident()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg")).andReturn().getResponse()
                .getContentAsString();
        String ticket = body(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","assetId":"%s","description":"Bloqué au 3e",
                 "photoFileIds":["%s"]}""".formatted(r.site(), ELEVATOR_STUCK, JsonPath.read(asset, "$.id"),
                JsonPath.read(photo, "$.id"))), r.resident());
        assertThat((String) JsonPath.read(ticket, "$.reference")).matches("T-\\d{6}");
        assertThat((String) JsonPath.read(ticket, "$.status.code")).isEqualTo("REQUESTED");
        assertThat((String) JsonPath.read(ticket, "$.location.building.id")).isEqualTo(r.building());
        assertThat((String) JsonPath.read(ticket, "$.location.address.addressLine")).isEqualTo("12 rue des Pins");
        assertThat((String) JsonPath.read(ticket, "$.visibility")).isEqualTo("COMMON");

        // The photo is read through its signed link only; a changed signature gives 404.
        String url = JsonPath.read(ticket, "$.photos[0].file.url");
        mvc.perform(get(url).contextPath("/api")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get(url.substring(0, url.length() - 2) + "AA").contextPath("/api"))
                .andExpect(status().isNotFound());
        // The same file cannot be attached twice.
        call(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","photoFileIds":["%s"]}""".formatted(r.site(), DIRTY_STAIRS,
                JsonPath.read(photo, "$.id"))), r.resident())
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("FILE_NOT_USABLE"));
    }

    @Test
    void H3_aTechnicalTypeNeedsAnEquipment() throws Exception {
        Residence r = residence();
        call(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s"}""".formatted(r.site(), ELEVATOR_STUCK)), r.resident())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TICKET_ASSET_REQUIRED"));
    }

    // ---------------------------------------------------------------------------------------- criterion 9

    @Test
    void criterion9_aTicketIsRoutedToTheManagerOrToTheDesignatedTechnicianByItsType() throws Exception {
        Residence r = residence();
        Person technician = technician(r);
        assertThat((List<String>) JsonPath.read(body(get("/v1/sites/" + r.site() + "/eligible-technicians"),
                r.manager()), "$[*].id")).contains(technician.id());
        String residentId = JsonPath.read(body(get("/v1/me"), r.resident()), "$.id");
        call(put("/v1/sites/" + r.site() + "/issue-type-configs/" + WATER_LEAK).content("""
                {"routing":"TECHNICIANS","technicianIds":["%s"],"visibility":"PRIVATE","active":true}"""
                .formatted(residentId)), r.manager()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TECHNICIAN_NOT_ELIGIBLE"));
        call(put("/v1/sites/" + r.site() + "/issue-type-configs/" + WATER_LEAK).content("""
                {"routing":"TECHNICIANS","technicianIds":["%s"],"visibility":"PRIVATE","active":true}"""
                .formatted(technician.id())), r.manager()).andExpect(status().isOk())
                .andExpect(jsonPath("$.routing").value("TECHNICIANS"));

        String leak = id(body(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","unitId":"%s"}""".formatted(r.site(), WATER_LEAK, r.unit())),
                r.resident()));
        String stairs = id(body(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","buildingId":"%s"}""".formatted(r.site(), DIRTY_STAIRS,
                r.building())), r.resident()));

        // C3, criterion 13: the technician sees the ticket routed to them, never the other one.
        call(get("/v1/tickets/" + leak), technician.bearer()).andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians[0].id").value(technician.id()));
        call(get("/v1/tickets/" + stairs), technician.bearer()).andExpect(status().isNotFound());
        assertThat((List<String>) JsonPath.read(body(get("/v1/tickets"), technician.bearer()), "$.items[*].id"))
                .containsExactly(leak);

        // K1, H4: the manager hears of both tickets, the technician of the one routed to them.
        String managerId = JsonPath.read(body(get("/v1/me"), r.manager()), "$.id");
        await(() -> notifications(managerId, "TICKET_CREATED") == 2);
        await(() -> notifications(technician.id(), "TICKET_ROUTED") == 1);
    }

    // --------------------------------------------------------------------------------------- criterion 10

    @Test
    void criterion10_commonTicketsAreReadOnlyForEveryResidentAndAPrivateOneStaysHidden() throws Exception {
        Residence r = residence();
        String otherUnit = createUnit(r.org().owner(), r.site(), "A-2", r.building());
        String neighbour = inviteResident(r.manager(), r.site(), otherUnit);
        String common = id(body(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","buildingId":"%s"}""".formatted(r.site(), DIRTY_STAIRS,
                r.building())), r.resident()));
        String privateOne = id(body(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","unitId":"%s","description":"Voisin bruyant"}"""
                .formatted(r.site(), NOISE, r.unit())), r.resident()));

        String seen = body(get("/v1/tickets?view=COMMON"), neighbour);
        assertThat((List<String>) JsonPath.read(seen, "$.items[*].id")).containsExactly(common);
        call(get("/v1/tickets/" + common), neighbour).andExpect(status().isOk())
                .andExpect(jsonPath("$.minimized").value(true)).andExpect(jsonPath("$.requester").doesNotExist());
        assertThat((List<?>) JsonPath.read(body(get("/v1/tickets/" + common + "/transitions"), neighbour), "$"))
                .isEmpty();
        call(post("/v1/tickets/" + common + "/transitions").content(
                "{\"transition\":\"cancel_by_requester\",\"version\":" + version(common, neighbour) + "}"), neighbour)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRANSITION_NOT_AVAILABLE"));
        call(get("/v1/tickets/" + privateOne), neighbour).andExpect(status().isNotFound());

        // The requester and the manager see both; another promoteur's manager sees nothing (criterion 13).
        assertThat((List<String>) JsonPath.read(body(get("/v1/tickets"), r.manager()), "$.items[*].id"))
                .containsExactlyInAnyOrder(common, privateOne);
        call(get("/v1/tickets/" + privateOne), r.manager()).andExpect(status().isOk())
                .andExpect(jsonPath("$.minimized").value(false))
                .andExpect(jsonPath("$.description").value("Voisin bruyant"));
        Residence other = residence();
        call(get("/v1/tickets/" + common), other.manager()).andExpect(status().isNotFound());
        assertThat((List<?>) JsonPath.read(body(get("/v1/tickets"), other.manager()), "$.items")).isEmpty();
    }

    // ----------------------------------------------------------------------------------- H5, H6, H7, C6

    @Test
    void H5_onHoldNeedsAReasonAndC6_nobodyClosesATicketTheyAskedFor() throws Exception {
        Residence r = residence();
        String own = id(body(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","buildingId":"%s"}""".formatted(r.site(), DIRTY_STAIRS,
                r.building())), r.manager()));
        fire(own, "take_charge", null, null, r.manager()).andExpect(status().isOk());
        fire(own, "put_on_hold", null, null, r.manager()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        fire(own, "put_on_hold", "OTHER", null, r.manager()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        fire(own, "put_on_hold", "PART", null, r.manager()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status.category").value("ON_HOLD"))
                .andExpect(jsonPath("$.status.holdReason").value("PART"));
        fire(own, "resume", null, null, r.manager()).andExpect(status().isOk());
        fire(own, "resolve", null, null, r.manager()).andExpect(status().isOk());
        fire(own, "close", null, null, r.manager()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_CLOSE_FORBIDDEN"));
        assertThat((List<String>) JsonPath.read(body(get("/v1/tickets/" + own + "/transitions"), r.manager()),
                "$[*].code")).doesNotContain("close");

        // H7, K3, criterion 21: a resident's ticket is closed by the manager; the resident is notified in the app.
        String ticket = id(body(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","buildingId":"%s"}""".formatted(r.site(), DIRTY_STAIRS,
                r.building())), r.resident()));
        fire(ticket, "take_charge", null, null, r.manager()).andExpect(status().isOk());
        fire(ticket, "resolve", null, null, r.manager()).andExpect(status().isOk());
        fire(ticket, "close", null, null, r.manager()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status.category").value("CLOSED"));
        String residentId = JsonPath.read(body(get("/v1/me"), r.resident()), "$.id");
        await(() -> notifications(residentId, "TICKET_STATUS_CHANGED") == 3);
        assertThat(count("SELECT count(*) FROM notification.notification WHERE recipient_id = '" + residentId
                + "' AND link = '/tickets/" + ticket + "'")).isEqualTo(3);

        // H9: every step is in the history, with who and the reason.
        String history = body(get("/v1/tickets/" + own + "/history"), r.manager());
        assertThat((List<String>) JsonPath.read(history, "$[?(@.kind=='STEP')].transition"))
                .containsSubsequence("take_charge", "put_on_hold", "resume", "resolve");
        assertThat((List<String>) JsonPath.read(history, "$[?(@.transition=='put_on_hold')].reason"))
                .containsExactly("PART");
        assertThat((List<String>) JsonPath.read(history, "$[?(@.activity=='CREATED')].actor.part"))
                .containsExactly("REQUESTER");
    }

    @Test
    void H6_theRequesterCancelsOnlyWhileRequestedAndTheManagerWithAReason() throws Exception {
        Residence r = residence();
        String first = id(body(post("/v1/tickets").content(stairs(r)), r.resident()));
        fire(first, "cancel_by_requester", null, null, r.resident()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status.category").value("CANCELLED"));

        String second = id(body(post("/v1/tickets").content(stairs(r)), r.resident()));
        fire(second, "take_charge", null, null, r.manager()).andExpect(status().isOk());
        fire(second, "cancel_by_requester", null, null, r.resident()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSITION_NOT_AVAILABLE"));
        fire(second, "cancel_by_manager", null, null, r.manager()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));
        fire(second, "cancel_by_manager", null, "Doublon", r.manager()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status.category").value("CANCELLED"));
    }

    // -------------------------------------------------------------------------------- criterion 19, H5

    @Test
    void criterion19_autoCloseAfterFiveDaysForOneClientLeavesAnotherClientUntouched() throws Exception {
        Residence chosen = residence();
        Residence untouched = residence();
        String closing = resolvedTicket(chosen);
        String staying = resolvedTicket(untouched);

        String admin = platformAdmin();
        String workflowId = JsonPath.read(body(get("/v1/admin/workflows"), admin), "$[0].id");
        call(put("/v1/admin/organizations/" + chosen.org().id() + "/workflow").content("""
                {"workflowId":"%s","settings":{"autoCloseAfterDays":5,"requesterValidation":false,"labels":{}}}"""
                .formatted(workflowId)), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.autoCloseAfterDays").value(5));
        call(put("/v1/admin/organizations/" + chosen.org().id() + "/workflow").content("""
                {"workflowId":"%s","settings":{"requesterValidation":false,"labels":{}}}""".formatted(workflowId)),
                chosen.org().owner()).andExpect(status().isForbidden());

        // Both tickets have been "resolved" for six days; only the chosen client closes them automatically.
        jdbc.update("""
                UPDATE workflow.workflow_token SET entered_at = now() - interval '6 days'
                WHERE active AND instance_id IN (SELECT workflow_instance_id FROM maintenance.ticket
                                                 WHERE id IN (?::uuid, ?::uuid))""", closing, staying);
        assertThat(autoClose.run()).isGreaterThanOrEqualTo(1);
        call(get("/v1/tickets/" + closing), chosen.manager()).andExpect(jsonPath("$.status.category").value("CLOSED"));
        call(get("/v1/tickets/" + staying), untouched.manager())
                .andExpect(jsonPath("$.status.category").value("RESOLVED"));
        assertThat((List<String>) JsonPath.read(body(get("/v1/tickets/" + closing + "/history"), chosen.manager()),
                "$[?(@.transition=='auto_close')].actor.part")).containsExactly("SYSTEM");
        await(() -> count("SELECT count(*) FROM audit.audit_entry WHERE action = 'WORKFLOW_BINDING_CHANGED' "
                + "AND subject_id = '" + chosen.org().id() + "'") == 1);
    }

    @Test
    void H5_withRequesterValidationTheManagerClosesOnlyOnceTheRequesterConfirmed() throws Exception {
        Residence r = residence();
        String admin = platformAdmin();
        String workflowId = JsonPath.read(body(get("/v1/admin/workflows"), admin), "$[0].id");
        call(put("/v1/admin/organizations/" + r.org().id() + "/workflow").content("""
                {"workflowId":"%s","settings":{"requesterValidation":true,
                 "labels":{"RESOLVED":{"fr":"Terminé"}}}}""".formatted(workflowId)), admin)
                .andExpect(status().isOk());
        String ticket = resolvedTicket(r);
        call(get("/v1/tickets/" + ticket), r.resident()).andExpect(jsonPath("$.status.label.fr").value("Terminé"));
        fire(ticket, "close", null, null, r.manager()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("REQUESTER_CONFIRMATION_REQUIRED"));
        assertThat((List<String>) JsonPath.read(body(get("/v1/tickets/" + ticket + "/transitions"), r.resident()),
                "$[*].code")).containsExactlyInAnyOrder("confirm_resolution", "reopen_by_requester");
        fire(ticket, "confirm_resolution", null, null, r.resident()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status.category").value("RESOLVED"));
        fire(ticket, "close", null, null, r.manager()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status.category").value("CLOSED"));
    }

    // ------------------------------------------------------------------------------------------ files

    @Test
    void ADR038_filesAreCheckedByTheirContentAndUsableOnlyByTheirUploader() throws Exception {
        Residence r = residence();
        upload("GIF89a not a jpeg".getBytes(), "photo.jpg", "TICKET_PHOTO", r.resident())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CONTENT"));
        upload("%PDF-1.7\n%âãÏÓ\n".getBytes(), "devis.pdf", "TICKET_PHOTO", r.resident())
                .andExpect(status().isUnsupportedMediaType());
        upload("%PDF-1.7\n%âãÏÓ\n".getBytes(), "notice.pdf", "ASSET_DOCUMENT", r.manager())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.contentType").value("application/pdf"));
        String someoneElse = JsonPath.read(upload(png(), "p.png", "TICKET_PHOTO", r.manager())
                .andReturn().getResponse().getContentAsString(), "$.id");
        call(post("/v1/tickets").content("""
                {"siteId":"%s","issueTypeId":"%s","photoFileIds":["%s"]}""".formatted(r.site(), DIRTY_STAIRS,
                someoneElse)), r.resident()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FILE_NOT_USABLE"));
        mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", "p.png", "image/png", png()))
                .param("purpose", "TICKET_PHOTO")).andExpect(status().isUnauthorized());
    }

    // ----------------------------------------------------------------------------- equipment import, G1

    @Test
    void ADR027_equipmentIsImportedAllOrNothingWithItsQrCode() throws Exception {
        Residence r = residence();
        byte[] template = mvc.perform(get("/v1/assets/import-template?language=FR&siteId=" + r.site())
                        .header(HttpHeaders.AUTHORIZATION, r.manager()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        List<Map<String, String>> rows = List.of(
                Map.of("name", "Ascenseur B", "family", "ELEVATOR", "model", "Gen2", "building", "Bâtiment A"),
                Map.of("name", "Extincteur 1", "family", "FIRE_EXTINGUISHER", "floor", "2", "building", "Bâtiment A"),
                Map.of("name", "Portail", "family", "GATE", "state", "OUT_OF_ORDER"));
        List<Map<String, String>> wrong = new java.util.ArrayList<>(rows);
        wrong.add(Map.of("name", "Piscine", "family", "POOL"));
        importAssets(r, fill(template, wrong), r.manager()).andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.errors[0].code").value("IMPORT_FAMILY_UNKNOWN"))
                .andExpect(jsonPath("$.errors[0].row").value(6));
        importAssets(r, fill(template, rows), r.manager()).andExpect(jsonPath("$.outcome").value("IMPORTED"))
                .andExpect(jsonPath("$.createdCount").value(3));
        String page = body(get("/v1/sites/" + r.site() + "/assets"), r.manager());
        assertThat((List<String>) JsonPath.read(page, "$.items[*].qrCode")).hasSize(3).doesNotContainNull();
        importAssets(r, fill(template, rows), r.resident()).andExpect(status().isForbidden());
    }

    // --------------------------------------------------------------------------------------- ADR-036

    @Test
    void ADR036_aDraftCircuitIsCheckedBeforeItIsPublished() throws Exception {
        String admin = platformAdmin();
        UUID workflow = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        UUID open = UUID.randomUUID();
        UUID done = UUID.randomUUID();
        jdbc.update("INSERT INTO workflow.workflow (id, owner_scope, subject_type, code, name) "
                + "VALUES (?, 'PLATFORM', 'TICKET', ?, 'Test')", workflow, "test-" + workflow);
        jdbc.update("INSERT INTO workflow.workflow_version (id, workflow_id, number) VALUES (?, ?, 1)", version,
                workflow);
        jdbc.update("INSERT INTO workflow.workflow_node (id, version_id, code, category) VALUES (?, ?, 'NEW', 'OPEN')",
                open, version);
        jdbc.update("INSERT INTO workflow.workflow_node (id, version_id, code, category) "
                + "VALUES (?, ?, 'DONE', 'RESOLVED')", done, version);
        jdbc.update("INSERT INTO workflow.workflow_transition (version_id, code, from_node_id, to_node_id, guards) "
                + "VALUES (?, 'finish', ?, ?, '[{\"code\":\"Teleport\"}]')", version, open, done);
        call(post("/v1/admin/workflows/" + workflow + "/versions/1/publish"), admin)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WORKFLOW_DEFINITION_INVALID"))
                .andExpect(jsonPath("$.violations[*].field").value(org.hamcrest.Matchers.hasItems("closed",
                        "cancelled", "guard:finish:Teleport")));
        String defaultId = JsonPath.read(body(get("/v1/admin/workflows"), admin), "$[0].id");
        call(post("/v1/admin/workflows/" + defaultId + "/versions/1/publish"), admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKFLOW_VERSION_PUBLISHED"));
    }

    // ------------------------------------------------------------------------------------------- helpers

    private record Organization(String id, String owner) {
    }

    private record Person(String id, String bearer) {
    }

    /** A residence with a building, a manager and a resident owning one apartment of the building. */
    private record Residence(Organization org, String site, String building, String unit, String manager,
                             String resident) {
    }

    /** A new residence ready for tickets. */
    private Residence residence() throws Exception {
        Organization org = onboard();
        String site = JsonPath.read(call(post("/v1/organizations/" + org.id() + "/sites").content("""
                {"type":"RESIDENTIAL","name":"Résidence Les Pins","lifecycleStatus":"OPERATING",
                 "address":{"zoneId":"%s","addressLine":"12 rue des Pins"}}""".formatted(zone("1601"))), org.owner())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String building = JsonPath.read(call(post("/v1/sites/" + site + "/buildings").content(
                "{\"name\":\"Bâtiment A\",\"buildingUse\":\"RESIDENTIAL\",\"floorCount\":5}"), org.owner())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String unit = createUnit(org.owner(), site, "A-1", building);
        String manager = invite(org, "SITE_MANAGER", site).bearer();
        return new Residence(org, site, building, unit, manager, inviteResident(manager, site, unit));
    }

    /** A technician given on the residence by its promoteur. */
    private Person technician(Residence r) throws Exception {
        return invite(r.org(), "TECHNICIAN", r.site());
    }

    /** A resident's common ticket taken and resolved by the manager. */
    private String resolvedTicket(Residence r) throws Exception {
        String ticket = id(body(post("/v1/tickets").content(stairs(r)), r.resident()));
        fire(ticket, "take_charge", null, null, r.manager()).andExpect(status().isOk());
        fire(ticket, "resolve", null, null, r.manager()).andExpect(status().isOk());
        return ticket;
    }

    /** Body of a common ticket on the stairs of the building. */
    private static String stairs(Residence r) {
        return """
                {"siteId":"%s","issueTypeId":"%s","buildingId":"%s"}""".formatted(r.site(), DIRTY_STAIRS,
                r.building());
    }

    /** Takes a passage with the version read just before. */
    private ResultActions fire(String ticket, String transition, String reason, String comment, String bearer)
            throws Exception {
        Map<String, Object> fields = new HashMap<>();
        fields.put("transition", transition);
        fields.put("version", version(ticket, bearer));
        if (reason != null) {
            fields.put("reason", reason);
        }
        if (comment != null) {
            fields.put("comment", comment);
        }
        StringBuilder json = new StringBuilder("{");
        fields.forEach((k, v) -> json.append(json.length() > 1 ? "," : "").append('"').append(k).append("\":")
                .append(v instanceof String ? "\"" + v + "\"" : v));
        return call(post("/v1/tickets/" + ticket + "/transitions").content(json.append('}').toString()), bearer);
    }

    /** The current version of a ticket as this reader sees it. */
    private long version(String ticket, String bearer) {
        Number version = JsonPath.read(body(get("/v1/tickets/" + ticket), bearer), "$.version");
        return version.longValue();
    }

    /** Uploads a file. */
    private ResultActions upload(byte[] content, String name, String purpose, String bearer) throws Exception {
        return mvc.perform(multipart("/v1/files").file(new MockMultipartFile("file", name, "image/jpeg", content))
                .param("purpose", purpose).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /** Uploads an equipment import file. */
    private ResultActions importAssets(Residence r, byte[] file, String bearer) throws Exception {
        return mvc.perform(multipart("/v1/sites/" + r.site() + "/assets/import")
                .file(new MockMultipartFile("file", "assets.xlsx", XLSX.toString(), file))
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    /** A small PNG image, as a phone would send before shrinking. */
    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, 64, 48);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** Fills the rows of a template below its labels, matching the hidden key row. */
    private static byte[] fill(byte[] template, List<Map<String, String>> rows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            Row keys = sheet.getRow(0);
            Map<String, Integer> columns = new HashMap<>();
            for (int c = 0; c < keys.getLastCellNum(); c++) {
                columns.put(keys.getCell(c).getStringCellValue(), c);
            }
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i + 2);
                rows.get(i).forEach((key, value) -> row.createCell(columns.get(key)).setCellValue(value));
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** A promoteur onboarded by our team, signed in. */
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

    /** A person given a role on the site by the promoteur, signed in. */
    private Person invite(Organization org, String role, String site) throws Exception {
        String email = newEmail();
        call(post("/v1/organizations/" + org.id() + "/invitations").content("""
                {"person":{"firstName":"Nadia","lastName":"%s","email":"%s","phone":"%s","language":"AR"},
                 "roles":[{"roleCode":"%s","scope":{"type":"SITE","id":"%s"}}]}"""
                .formatted(role, email, newPhone(), role, site)), org.owner()).andExpect(status().isCreated());
        String bearer = accept(email);
        return new Person(JsonPath.read(body(get("/v1/me"), bearer), "$.id"), bearer);
    }

    /** A resident owning a unit, created by the manager, signed in once their access is active. */
    private String inviteResident(String manager, String site, String unit) throws Exception {
        String email = newEmail();
        call(post("/v1/sites/" + site + "/invitations").content("""
                {"person":{"firstName":"Samir","lastName":"Resident","email":"%s","phone":"%s","language":"FR"},
                 "occupancies":[{"unitId":"%s","role":"OWNER"}]}""".formatted(email, newPhone(), unit)), manager)
                .andExpect(status().isCreated());
        String bearer = accept(email);
        // The access opens once the account is active (event PersonActivatedV1, handled after the commit).
        await(() -> body(get("/v1/me/access"), bearer).contains(unit));
        return bearer;
    }

    /** An apartment of the building with the default T3 category. */
    private String createUnit(String bearer, String site, String reference, String building) throws Exception {
        String category = jdbc.queryForObject("SELECT id::text FROM sites.unit_category WHERE scope_type = 'PLATFORM' "
                + "AND unit_type = 'APARTMENT' AND code = 'T3'", String.class);
        return JsonPath.read(call(post("/v1/sites/" + site + "/units").content("""
                {"type":"APARTMENT","categoryId":"%s","reference":"%s","buildingId":"%s","saleable":true,
                 "rentable":true}""".formatted(category, reference, building)), bearer)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** Uses the "set my password" link received by this e-mail and returns the {@code Authorization} header. */
    private String accept(String email) throws Exception {
        String token = mail.lastSecret(email, "invitation").orElseThrow();
        String session = mvc.perform(post("/api/v1/invitations/accept").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(session, "$.accessToken");
    }

    /** Number of notifications of a template received by a person. */
    private long notifications(String person, String template) {
        return count("SELECT count(*) FROM notification.notification WHERE recipient_id = '" + person
                + "' AND template = '" + template + "'");
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

    /** The identifier in a JSON body. */
    private static String id(String json) {
        return JsonPath.read(json, "$.id");
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

    /** A new e-mail address. */
    private static String newEmail() {
        return "s3-" + UUID.randomUUID().toString().substring(0, 8) + "@example.dz";
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
