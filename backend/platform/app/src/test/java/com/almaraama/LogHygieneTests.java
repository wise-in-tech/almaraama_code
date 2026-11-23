package com.almaraama;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.InvitePerson;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.i18n.Language;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * ADR-033: with the debug mode at its most detailed level ({@code TRACE}), a whole account journey (set my password,
 * sign-in, forgotten password) leaves no password, no link secret and no full e-mail address in the technical logs.
 */
@SpringBootTest(properties = {"almaraama.security.auth-requests-per-minute=10000", "logging.level.almaraama=TRACE"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LogHygieneTests extends PostgresIntegrationTest {

    private static final String PASSWORD = "Mot de passe à ne jamais journaliser";

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityApi identity;

    @Autowired
    RecordingEmailSender mail;

    @Test
    void ADR033_theDebugModeNeverLogsSecretsNorPersonalData(CapturedOutput output) throws Exception {
        String email = "hygiene-" + UUID.randomUUID().toString().substring(0, 8) + "@example.dz";
        identity.invitePerson(new InvitePerson("Amina", "Benali", email, "+213661234567", Language.FR,
                Actor.platform()));
        String invitation = mail.lastSecret(email, "invitation").orElseThrow();
        post("/v1/invitations/accept", "{\"token\":\"" + invitation + "\",\"password\":\"" + PASSWORD + "\"}", null);
        post("/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null);
        post("/v1/auth/password-reset", "{\"email\":\"" + email + "\"}", AltchaTestSupport.solve(mvc));
        String reset = mail.lastSecret(email, "reset-password").orElseThrow();

        assertThat(output.getAll()).contains("Account e-mail SET_PASSWORD sent")
                .doesNotContain(PASSWORD)
                .doesNotContain(invitation)
                .doesNotContain(reset)
                .doesNotContain(email);
    }

    /** A JSON POST under the /api context path, with an optional ALTCHA proof. */
    private void post(String path, String body, String altcha) throws Exception {
        var request = MockMvcRequestBuilders.post("/api" + path).contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (altcha != null) {
            request.header("X-Altcha", altcha);
        }
        mvc.perform(request).andExpect(status().is2xxSuccessful());
    }
}
