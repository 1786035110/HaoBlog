package io.haoblog;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class AdminSessionIT {
    private static final String PASSWORD_HASH = "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.admin_username", () -> "admin");
        registry.add("spring.flyway.placeholders.admin_password_hash", () -> PASSWORD_HASH);
        registry.add("haoblog.site.public-base-url", () -> "https://blog.example.test");
    }

    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void resetIdentityAndSessions() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("UPDATE admin_user SET last_login_at = NULL");
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
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class));
    }

    @Test
    void rejectsMissingCsrfAndLimitsOnlyTheFailedSource() throws Exception {
        CsrfSession session = csrfSession();
        RequestPostProcessor sourceA = remoteAddr("10.0.0.1");
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/admin/session").cookie(session.cookie())
                            .with(sourceA)
                            .contentType("application/json")
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        }

        String token = session.token();
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/api/v1/admin/session").cookie(session.cookie())
                            .with(sourceA)
                            .header("X-CSRF-TOKEN", token)
                            .contentType("application/json")
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }
        mvc.perform(post("/api/v1/admin/session").cookie(session.cookie())
                        .with(sourceA)
                        .header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"correct\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, org.hamcrest.Matchers.matchesPattern("[1-9][0-9]{0,2}")))
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));

        RequestPostProcessor sourceB = remoteAddr("10.0.0.2");
        for (int attempt = 0; attempt < 4; attempt++) {
            mvc.perform(post("/api/v1/admin/session").cookie(session.cookie())
                            .with(sourceB)
                            .header("X-CSRF-TOKEN", token)
                            .contentType("application/json")
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        var loginFromSourceB = mvc.perform(post("/api/v1/admin/session").cookie(session.cookie())
                        .with(sourceB)
                        .header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sourceBSession = loginFromSourceB.getResponse().getCookie("HAOBLOG_SESSION");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/admin/session").cookie(sourceBSession)
                            .with(sourceB)
                            .header("X-CSRF-TOKEN", token)
                            .contentType("application/json")
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void removesAccountLockingColumnsFromAdminUser() {
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name = 'admin_user' AND column_name IN ('failed_login_attempts', 'locked_until')", Integer.class));
    }

    @Test
    void logsInRestoresSessionAndLogsOut() throws Exception {
        CsrfSession session = csrfSession();
        var login = mvc.perform(post("/api/v1/admin/session").cookie(session.cookie())
                        .header("X-CSRF-TOKEN", session.token())
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        Cookie authenticated = login.getResponse().getCookie("HAOBLOG_SESSION");

        mvc.perform(get("/api/v1/admin/session").cookie(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(delete("/api/v1/admin/session").cookie(authenticated)
                        .header("X-CSRF-TOKEN", session.token()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/session").cookie(authenticated))
                .andExpect(status().isUnauthorized());
    }

    private CsrfSession csrfSession() throws Exception {
        var result = mvc.perform(get("/api/v1/admin/csrf")).andExpect(status().isOk()).andReturn();
        return new CsrfSession(result.getResponse().getCookie("HAOBLOG_SESSION"),
                JsonPath.read(result.getResponse().getContentAsString(), "$.token"));
    }

    private static RequestPostProcessor remoteAddr(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private record CsrfSession(Cookie cookie, String token) {}

}
