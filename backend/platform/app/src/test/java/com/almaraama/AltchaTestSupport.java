package com.almaraama;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.altcha.altcha.v1.Altcha;
import org.springframework.test.web.servlet.MockMvc;

/** Solves an ALTCHA challenge as the widget does (ADR-032), for the tests of the public forms. */
final class AltchaTestSupport {

    /** Static helpers only. */
    private AltchaTestSupport() {
    }

    /** Fetches a challenge from the API, solves it and returns the {@code X-Altcha} header value. */
    static String solve(MockMvc mvc) throws Exception {
        String body = mvc.perform(get("/api/v1/altcha/challenge").contextPath("/api")).andReturn().getResponse()
                .getContentAsString();
        String challenge = JsonPath.read(body, "$.challenge");
        String salt = JsonPath.read(body, "$.salt");
        String signature = JsonPath.read(body, "$.signature");
        Number max = JsonPath.read(body, "$.maxnumber");
        Altcha.Solution solution = Altcha.solveChallenge(challenge, salt, Altcha.Algorithm.SHA256, max.longValue(), 0);
        String payload = "{\"algorithm\":\"SHA-256\",\"challenge\":\"" + challenge + "\",\"number\":"
                + solution.number() + ",\"salt\":\"" + salt + "\",\"signature\":\"" + signature + "\"}";
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
