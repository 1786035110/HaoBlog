package io.haoblog;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class AdminSessionIT {
    private static final String PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.admin_username", () -> "admin");
        registry.add("spring.flyway.placeholders.admin_password_hash", () -> PASSWORD_HASH);
    }

    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void resetIdentityAndSessions() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("UPDATE admin_user SET failed_login_attempts = 0, locked_until = NULL, last_login_at = NULL");
    }

    @Test
    void protectsAdminSessionAndKeepsPublicApiAnonymous() throws Exception {
        mvc.perform(get("/api/v1/public/site")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void rejectsAuthenticatedNonAdminWithForbiddenProblem() throws Exception {
        mvc.perform(get("/api/v1/admin/session").with(user("reader").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void getsCsrfTokenAndSetsSecureSessionCookie() throws Exception {
        var result = mvc.perform(get("/api/v1/admin/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("HAOBLOG_SESSION="),
                        org.hamcrest.Matchers.containsStringIgnoringCase("HttpOnly"),
                        org.hamcrest.Matchers.containsStringIgnoringCase("SameSite=Lax"),
                        org.hamcrest.Matchers.containsStringIgnoringCase("Secure"))))
                .andReturn();
        MockHttpSession session = (MockHttpSession) Objects.requireNonNull(result.getRequest().getSession(false));
        assertTrueSessionRowExists(session.getId());
    }

    @Test
    void rejectsMissingCsrfAndLocksAfterFiveBadPasswords() throws Exception {
        MockHttpSession session = csrfSession();
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/admin/session").session(session)
                            .contentType("application/json")
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }

        String token = csrfToken(session);
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/admin/session").session(session)
                            .header("X-CSRF-TOKEN", token)
                            .contentType("application/json")
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }
        mvc.perform(post("/api/v1/admin/session").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"correct\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void logsInRestoresSessionAndLogsOut() throws Exception {
        MockHttpSession session = csrfSession();
        String token = csrfToken(session);
        var login = mvc.perform(post("/api/v1/admin/session").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        MockHttpSession authenticated = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/v1/admin/session").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(delete("/api/v1/admin/session").session(authenticated)
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/session").session(authenticated))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession csrfSession() throws Exception {
        var result = mvc.perform(get("/api/v1/admin/csrf")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) Objects.requireNonNull(result.getRequest().getSession(false));
    }

    private String csrfToken(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/api/v1/admin/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private void assertTrueSessionRowExists(String sessionId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE session_id = ?", Integer.class, sessionId);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }
}
