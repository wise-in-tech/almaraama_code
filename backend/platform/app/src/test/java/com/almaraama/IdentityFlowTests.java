package com.almaraama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.InvitePerson;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.i18n.Language;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Identity journeys through the HTTP API on a real database (ADR-030 to ADR-033, criterion 7): "set my password"
 * link, sign-in by e-mail and password, e-mail verification, forgotten password, lockout, session renewal and
 * sign-out, the ALTCHA proof, the sending limit, the request identifier and the journal (D1, D4, D5, M2). Each test
 * uses its own e-mail address. E-mails are recorded by {@link RecordingEmailSender}, never sent.
 */
@SpringBootTest(properties = "almaraama.security.auth-requests-per-minute=10000")
@AutoConfigureMockMvc
class IdentityFlowTests extends PostgresIntegrationTest {

    private static final String GOOD_PASSWORD = "Cèdre bleu sur la colline";
    private static final String CSRF = "X-Almaraama-Csrf";

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityApi identity;

    @Autowired
    RecordingEmailSender mail;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationContext context;

    // ---------------------------------------------------------------------------------- D4: set my password

    @Test
    void D4_invitedPersonSetsPasswordWhichVerifiesTheEmailAndSignsIn() throws Exception {
        String email = newEmail();
        String token = invite(email);

        mvc.perform(apiPost("/v1/invitations/inspect").content(json("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Amina"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.language").value("FR"));

        MvcResult accepted = mvc.perform(apiPost("/v1/invitations/accept")
                        .content(json("token", token, "password", GOOD_PASSWORD, "deviceName", "Test phone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andReturn();

        assertThat(accepted.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("almaraama_refresh=",
                "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
        mvc.perform(apiGet("/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(accepted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerifiedAt").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.phone").exists());
    }

    @Test
    void D4_theLinkIsSentByEmailInThePersonsLanguageAndNeverReturned() throws Exception {
        String email = newEmail();
        identity.invitePerson(new InvitePerson("Amina", "Benali", email, newPhone(), Language.AR, Actor.platform()));

        assertThat(mail.awaitAtLeast(email, 1)).hasSize(1);
        assertThat(mail.to(email).getFirst().subject()).contains("المرور");
        assertThat(mail.to(email).getFirst().html()).contains("dir=\"rtl\"");
        assertThat(mail.lastSecret(email, "invitation")).isPresent();
    }

    @Test
    void D4_aSetPasswordLinkWorksOnlyOnce() throws Exception {
        String token = invite(newEmail());
        mvc.perform(apiPost("/v1/invitations/accept").content(json("token", token, "password", GOOD_PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(apiPost("/v1/invitations/accept").content(json("token", token, "password", GOOD_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_INVALID_OR_EXPIRED"));
    }

    @Test
    void ADR030_anUnknownLinkGivesTheSameAnswer() throws Exception {
        mvc.perform(apiPost("/v1/invitations/inspect").content(json("token", "x".repeat(43))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_INVALID_OR_EXPIRED"));
        mvc.perform(apiPost("/v1/accounts/verify-email").content(json("token", "y".repeat(43))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_INVALID_OR_EXPIRED"));
    }

    @Test
    void D5_aWeakPasswordIsRefusedAndTheLinkStaysUsable() throws Exception {
        String email = newEmail();
        String token = invite(email);
        expectRefused(token, "court", "PASSWORD_TOO_SHORT");
        expectRefused(token, "password123", "PASSWORD_TOO_COMMON");
        expectRefused(token, email.substring(0, email.indexOf('@')), "PASSWORD_SAME_AS_EMAIL");
        expectRefused(token, "a".repeat(129), "PASSWORD_TOO_LONG");
        mvc.perform(apiPost("/v1/invitations/accept").content(json("token", token, "password", GOOD_PASSWORD)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------- D5: sign-in, lockout

    @Test
    void D5_signInWithTheEmailWhateverItsCase() throws Exception {
        String email = activePerson();
        mvc.perform(apiPost("/v1/auth/login").content(json("email", "  " + email.toUpperCase() + " ",
                        "password", GOOD_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void D1_aForeignMobileIsAcceptedAndTwoPeopleMayShareAPhone() throws Exception {
        String phone = "+33612345678";
        String first = newEmail();
        String second = newEmail();
        identity.invitePerson(new InvitePerson("Karim", "Haddad", first, "0033 6 12 34 56 78", Language.FR,
                Actor.platform()));
        identity.invitePerson(new InvitePerson("Leila", "Haddad", second, phone, Language.FR, Actor.platform()));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.person WHERE phone = ?", Integer.class, phone))
                .isEqualTo(2);
    }

    @Test
    void D5_aWrongPasswordAndAnUnknownEmailGiveTheSameAnswer() throws Exception {
        String email = activePerson();
        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", "wrong password!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        mvc.perform(apiPost("/v1/auth/login").content(json("email", newEmail(), "password", GOOD_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void D5_fiveFailuresLockTheAccountEvenForTheRightPassword() throws Exception {
        String email = activePerson();
        for (int i = 0; i < 4; i++) {
            mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", "wrong password!")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", "wrong password!")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_LOCKED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", GOOD_PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void D5_anUnknownEmailIsLockedTheSameWaySoNoAccountIsRevealed() throws Exception {
        String email = newEmail();
        for (int i = 0; i < 4; i++) {
            mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", "whatever-it-is")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", "whatever-it-is")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_LOCKED"));
    }

    @Test
    void D5_aMalformedRequestIsAValidationProblem() throws Exception {
        mvc.perform(apiPost("/v1/auth/login").content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    // ------------------------------------------------------------------------------ D1: e-mail verification

    @Test
    void D1_anUnverifiedAccountCannotSignInUntilItVerifiesItsEmail() throws Exception {
        String email = pendingVerificationPerson();
        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", GOOD_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", "wrong password!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

        mvc.perform(apiPost("/v1/accounts/verify-email/resend").header("X-Altcha", AltchaTestSupport.solve(mvc))
                        .content(json("email", email)))
                .andExpect(status().isAccepted());
        String token = mail.lastSecret(email, "verify-email").orElseThrow();
        mvc.perform(apiPost("/v1/accounts/verify-email").content(json("token", token)))
                .andExpect(status().isNoContent());

        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", GOOD_PASSWORD)))
                .andExpect(status().isOk());
    }

    // --------------------------------------------------------------------------- D5: forgotten password

    @Test
    void D5_forgottenPasswordSendsALinkWhoseUseEndsEverySession() throws Exception {
        String email = activePerson();
        MvcResult session = login(email, GOOD_PASSWORD);

        mvc.perform(apiPost("/v1/auth/password-reset").header("X-Altcha", AltchaTestSupport.solve(mvc))
                        .content(json("email", email)))
                .andExpect(status().isAccepted());
        String token = mail.lastSecret(email, "reset-password").orElseThrow();
        String newPassword = "Un autre mot de passe solide";
        mvc.perform(apiPost("/v1/auth/password-reset/confirm").content(json("token", token, "password", newPassword)))
                .andExpect(status().isNoContent());

        mvc.perform(apiPost("/v1/auth/refresh").header(CSRF, "1").cookie(refreshCookie(session)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", GOOD_PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(email, newPassword);
        assertThat(mail.awaitAtLeast(email, 3)).anyMatch(m -> m.text().contains("changé"));
        mvc.perform(apiPost("/v1/auth/password-reset/confirm").content(json("token", token, "password", newPassword)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LINK_INVALID_OR_EXPIRED"));
    }

    @Test
    void D5_forgottenPasswordAnswersTheSameForAnUnknownEmailAndSendsNothing() throws Exception {
        String email = newEmail();
        mvc.perform(apiPost("/v1/auth/password-reset").header("X-Altcha", AltchaTestSupport.solve(mvc))
                        .content(json("email", email)))
                .andExpect(status().isAccepted());
        assertThat(mail.settled(email)).isEmpty();
    }

    @Test
    void ADR031_atMostThreeEmailsOfOneKindPerHour() throws Exception {
        String email = activePerson();
        for (int i = 0; i < 4; i++) {
            mvc.perform(apiPost("/v1/auth/password-reset").header("X-Altcha", AltchaTestSupport.solve(mvc))
                            .content(json("email", email)))
                    .andExpect(status().isAccepted());
        }
        mail.awaitAtLeast(email, 4);
        assertThat(mail.settled(email).stream().filter(m -> m.text().contains("/reset-password#")).count())
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM identity.email_dispatch d JOIN identity.person p ON p.id = d.person_id
                WHERE p.email = ? AND d.outcome = 'THROTTLED'""", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void ADR032_publicFormsNeedAFreshAltchaProof() throws Exception {
        mvc.perform(apiPost("/v1/auth/password-reset").content(json("email", newEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALTCHA_INVALID"));
        String proof = AltchaTestSupport.solve(mvc);
        mvc.perform(apiPost("/v1/auth/password-reset").header("X-Altcha", proof).content(json("email", newEmail())))
                .andExpect(status().isAccepted());
        mvc.perform(apiPost("/v1/auth/password-reset").header("X-Altcha", proof).content(json("email", newEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALTCHA_INVALID"));
    }

    @Test
    void criterion7_noSmsIsEverSent() {
        assertThat(Arrays.stream(context.getBeanDefinitionNames()))
                .noneMatch(name -> name.toLowerCase().contains("sms"));
    }

    // ----------------------------------------------------------------------------------------- sessions

    @Test
    void renewalReplacesTheRefreshTokenAndAReusedOneEndsTheSession() throws Exception {
        MvcResult first = login(activePerson(), GOOD_PASSWORD);
        Cookie original = refreshCookie(first);

        MvcResult renewed = mvc.perform(apiPost("/v1/auth/refresh").header(CSRF, "1").cookie(original))
                .andExpect(status().isOk())
                .andReturn();
        Cookie replacement = refreshCookie(renewed);
        assertThat(replacement.getValue()).isNotEqualTo(original.getValue());

        mvc.perform(apiPost("/v1/auth/refresh").header(CSRF, "1").cookie(original))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        mvc.perform(apiPost("/v1/auth/refresh").header(CSRF, "1").cookie(replacement))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renewalAndLogoutRequireTheCsrfHeader() throws Exception {
        Cookie cookie = refreshCookie(login(activePerson(), GOOD_PASSWORD));
        mvc.perform(apiPost("/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_HEADER_MISSING"));
        mvc.perform(apiPost("/v1/auth/logout").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsTheSessionOnThisDevice() throws Exception {
        Cookie cookie = refreshCookie(login(activePerson(), GOOD_PASSWORD));
        mvc.perform(apiPost("/v1/auth/logout").header(CSRF, "1").cookie(cookie))
                .andExpect(status().isNoContent());
        mvc.perform(apiPost("/v1/auth/refresh").header(CSRF, "1").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void M3_theApiNeedsAValidAccessToken() throws Exception {
        mvc.perform(apiGet("/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(apiGet("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void ADR033_everyErrorCarriesTheRequestIdOfItsHeader() throws Exception {
        MvcResult result = mvc.perform(apiGet("/v1/me")).andExpect(status().isUnauthorized()).andReturn();
        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.requestId"))
                .isEqualTo(requestId);
    }

    // ----------------------------------------------------------------------------------------- data and M2

    @Test
    void noSecretIsStoredInClear() throws Exception {
        String email = newEmail();
        String token = invite(email);
        mvc.perform(apiPost("/v1/invitations/accept").content(json("token", token, "password", GOOD_PASSWORD)))
                .andExpect(status().isOk());

        String hash = jdbc.queryForObject("""
                SELECT c.password_hash FROM identity.credential c JOIN identity.person p ON p.id = c.person_id
                WHERE p.email = ?""", String.class, email);
        assertThat(hash).startsWith("$argon2id$").doesNotContain(GOOD_PASSWORD);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity.account_token WHERE token_hash = ?",
                Integer.class, token)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.event_publication WHERE serialized_event LIKE ?",
                Integer.class, "%" + token + "%")).isZero();
    }

    @Test
    void M2_signInsAndPasswordsAreJournaled() throws Exception {
        String email = activePerson();
        login(email, GOOD_PASSWORD);
        String personId = jdbc.queryForObject("SELECT id::text FROM identity.person WHERE email = ?", String.class,
                email);

        awaitAtMost(Duration.ofSeconds(10), () -> jdbc.queryForObject("""
                SELECT count(DISTINCT action) FROM audit.audit_entry
                WHERE subject_id = ?::uuid AND action IN ('PERSON_ACTIVATED', 'LOGIN_SUCCEEDED', 'PASSWORD_SET',
                                                          'EMAIL_VERIFIED', 'INVITATION_ISSUED')""",
                Integer.class, personId) == 5);
    }

    @Test
    void M2_theJournalIsAppendOnly() {
        String sql = "SELECT has_table_privilege(current_user, 'audit.audit_entry', ?)";
        assertThat(jdbc.queryForObject(sql, Boolean.class, "INSERT")).isTrue();
        assertThat(jdbc.queryForObject(sql, Boolean.class, "UPDATE")).isFalse();
        assertThat(jdbc.queryForObject(sql, Boolean.class, "DELETE")).isFalse();
    }

    // -------------------------------------------------------------------------------------------- helpers

    /** The API is served under the /api context path, as in api/openapi.yaml. */
    private static MockHttpServletRequestBuilder apiPost(String path) {
        return post("/api" + path).contextPath("/api").contentType(MediaType.APPLICATION_JSON);
    }

    /** A GET under the /api context path. */
    private static MockHttpServletRequestBuilder apiGet(String path) {
        return get("/api" + path).contextPath("/api");
    }

    /** A new e-mail address, unique per test. */
    private static String newEmail() {
        return "person-" + UUID.randomUUID().toString().substring(0, 8) + "@example.dz";
    }

    /** A real Algerian mobile range (066…): numbers are checked against the national numbering plan (ADR-028). */
    private static String newPhone() {
        return "+21366" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
    }

    /** Creates an account and returns the secret of the "set my password" link from the e-mail it received. */
    private String invite(String email) {
        identity.invitePerson(new InvitePerson("Amina", "Benali", email, newPhone(), Language.FR, Actor.platform()));
        return mail.lastSecret(email, "invitation").orElseThrow();
    }

    /** An active account with {@link #GOOD_PASSWORD}. */
    private String activePerson() throws Exception {
        String email = newEmail();
        mvc.perform(apiPost("/v1/invitations/accept").content(json("token", invite(email), "password",
                        GOOD_PASSWORD)))
                .andExpect(status().isOk());
        return email;
    }

    /**
     * An account that chose {@link #GOOD_PASSWORD} but has not verified its e-mail, as the self-registration of
     * sprint 2 will create it.
     */
    private String pendingVerificationPerson() {
        String email = newEmail();
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO identity.person (id, first_name, last_name, email, phone, language, status, created_at,
                                             updated_at, version)
                VALUES (?, 'Nadia', 'Saadi', ?, ?, 'FR', 'PENDING_VERIFICATION', ?, ?, 0)""",
                id, email, newPhone(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        String hash = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2).encode(GOOD_PASSWORD);
        jdbc.update("""
                INSERT INTO identity.credential (person_id, password_hash, failed_attempts, updated_at, version)
                VALUES (?, ?, 0, ?, 0)""", id, hash, java.sql.Timestamp.from(now));
        return email;
    }

    /** Signs in and returns the result, which carries the access token and the refresh cookie. */
    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(apiPost("/v1/auth/login").content(json("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** A password refused with the given code; the link stays usable. */
    private void expectRefused(String token, String password, String code) throws Exception {
        mvc.perform(apiPost("/v1/invitations/accept").content(json("token", token, "password", password)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(code));
    }

    /** The refresh cookie set by a response. */
    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("almaraama_refresh");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    /** The {@code Authorization} header value for the access token of a response. */
    private static String bearer(MvcResult result) throws Exception {
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    /** A flat JSON object of string values. */
    private static String json(String... keysAndValues) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(keysAndValues[i]).append("\":\"").append(keysAndValues[i + 1]).append('"');
        }
        return json.append('}').toString();
    }

    /** Waits for an eventually consistent condition (audit listeners). */
    private static void awaitAtMost(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition not met within " + timeout);
            }
            Thread.sleep(200);
        }
    }
}
