package com.almaraama;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Rate limiting of sign-in requests per client address (security baseline), with a low limit for the test. */
@SpringBootTest(properties = "almaraama.security.auth-requests-per-minute=3")
@AutoConfigureMockMvc
class AuthRateLimitTests extends PostgresIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void signInRequestsAreLimitedPerClient() throws Exception {
        String body = "{\"email\":\"nobody@example.dz\",\"password\":\"not-the-password\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").contextPath("/api").with(r -> {
                        r.setRemoteAddr("198.51.100.7");
                        return r;
                    }).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contextPath("/api").with(r -> {
                    r.setRemoteAddr("198.51.100.7");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }
}
