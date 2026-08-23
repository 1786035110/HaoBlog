package io.haoblog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ToolAdminIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:0.8.6-pg17");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.placeholders.admin_username", () -> "admin");
        registry.add("spring.flyway.placeholders.admin_password_hash", () -> "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanToolTables() {
        jdbc.execute("TRUNCATE tool, tool_category CASCADE");
    }

    @Test
    void migrationStartsWithEmptyToolTablesAndWritesNeedAuthAndCsrf() throws Exception {
        assertNotNull(jdbc.queryForObject("select to_regclass('tool_category')", String.class));
        assertNotNull(jdbc.queryForObject("select to_regclass('tool')", String.class));
        mvc.perform(get("/api/v1/admin/tool-categories"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentType("application/problem+json"));
        mvc.perform(post("/api/v1/admin/tool-categories").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No CSRF\",\"slug\":\"no-csrf\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void rejectsUnsafeComponentAndUrlCombinations() throws Exception {
        String categoryId = createCategory();
        tool(categoryId, "{\"type\":\"EMBEDDED\",\"componentKey\":\"<script>\",\"title\":\"Bad\",\"slug\":\"bad-component\",\"tags\":[]}")
                .andExpect(status().isBadRequest());
        tool(categoryId, "{\"type\":\"EMBEDDED\",\"componentKey\":\"json-format\",\"url\":\"https://example.com\",\"title\":\"Bad\",\"slug\":\"bad-fields\",\"tags\":[]}")
                .andExpect(status().isBadRequest());
        tool(categoryId, "{\"type\":\"LINK\",\"url\":\"http://example.com\",\"title\":\"Bad\",\"slug\":\"bad-http\",\"tags\":[]}")
                .andExpect(status().isBadRequest());
        tool(categoryId, "{\"type\":\"EMBEDDED\",\"componentKey\":\"json-format\",\"title\":\"JSON\",\"slug\":\"json-format\",\"tags\":[]}")
                .andExpect(status().isCreated());
    }

    @Test
    void filtersPaginatesUsesVersionAndProtectsCategoryDeletion() throws Exception {
        String categoryId = createCategory();
        String first = createTool(categoryId, "alpha-tool", "Alpha", "LINK", "https://example.com/alpha", null);
        createTool(categoryId, "beta-tool", "Beta", "SHOWCASE", "https://example.com/beta", null);

        mvc.perform(get("/api/v1/admin/tools?categoryId=" + categoryId + "&type=LINK&keyword=alpha&page=0&size=1")
                        .with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].slug").value("alpha-tool"));
        mvc.perform(put("/api/v1/admin/tools/" + first).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":99,\"categoryId\":\"" + categoryId + "\",\"type\":\"LINK\",\"status\":\"INACTIVE\",\"title\":\"Alpha\",\"slug\":\"alpha-tool\",\"url\":\"https://example.com/alpha\",\"tags\":[],\"sortOrder\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOOL_VERSION_CONFLICT"));
        JsonNode category = objectMapper.readTree(mvc.perform(get("/api/v1/admin/tool-categories/" + categoryId).with(admin()))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(delete("/api/v1/admin/tool-categories/" + categoryId + "?version=" + category.get("version").asLong()).with(admin()).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOOL_CATEGORY_IN_USE"));
    }

    @Test
    void databaseConstraintsRejectArbitraryComponentAndNonArrayTags() {
        UUID categoryId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into tool_category(id,name,slug,created_at,updated_at) values (?,?,?,?,?)", categoryId, "DB " + categoryId, "db-" + categoryId.toString().substring(0, 8), now, now);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("insert into tool(id,category_id,type,status,title,slug,component_key,tags,created_at,updated_at) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
                UUID.randomUUID(), categoryId, "EMBEDDED", "ACTIVE", "Invalid", "invalid-" + categoryId.toString().substring(0, 8), "arbitrary-path", "{}", now, now));
    }

    @Test
    void publicDirectoryReturnsActiveToolsValidCategoriesAndSupportsConditionalFilters() throws Exception {
        String categoryId = createCategory();
        createTool(categoryId, "json-link", "JSON Link", "LINK", "https://example.com/json", null);
        createTool(categoryId, "json-format", "JSON Format", "EMBEDDED", null, "json-format");
        tool(categoryId, "{\"type\":\"LINK\",\"url\":\"https://example.com/hidden\",\"title\":\"Hidden\",\"slug\":\"hidden\",\"tags\":[],\"status\":\"INACTIVE\"}")
                .andExpect(status().isCreated());

        var directory = mvc.perform(get("/api/v1/public/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", org.hamcrest.Matchers.is(2)))
                .andExpect(jsonPath("$.categories.length()", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.items[0].category.slug").isNotEmpty())
                .andExpect(jsonPath("$.items[0].tags").isArray())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("s-maxage=60")))
                .andExpect(header().exists("ETag"))
                .andReturn();
        String etag = directory.getResponse().getHeader("ETag");
        mvc.perform(get("/api/v1/public/tools").header("If-None-Match", etag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
        mvc.perform(get("/api/v1/public/tools?type=EMBEDDED&keyword=json"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.items[0].componentKey").value("json-format"));
        mvc.perform(get("/api/v1/public/tools?category=" + categoryId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()", org.hamcrest.Matchers.is(2)));
    }

    @Test
    void publicDirectoryHasExplicitEmptyState() throws Exception {
        mvc.perform(get("/api/v1/public/tools?keyword=not-present-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.categories").isEmpty());
    }

    private String createCategory() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var result = mvc.perform(post("/api/v1/admin/tool-categories").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Category " + suffix + "\",\"slug\":\"category-" + suffix + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions tool(String categoryId, String body) throws Exception {
        String base = body.substring(0, body.length() - 1);
        String payload = base + ",\"categoryId\":\"" + categoryId + "\""
                + (body.contains("\"status\"") ? "" : ",\"status\":\"ACTIVE\"") + "}";
        return mvc.perform(post("/api/v1/admin/tools").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private String createTool(String categoryId, String slug, String title, String type, String url, String componentKey) throws Exception {
        String body = "{\"categoryId\":\"" + categoryId + "\",\"type\":\"" + type + "\",\"status\":\"ACTIVE\",\"title\":\"" + title + "\",\"slug\":\"" + slug + "\",\"url\":" + (url == null ? "null" : "\"" + url + "\"") + ",\"componentKey\":" + (componentKey == null ? "null" : "\"" + componentKey + "\"") + ",\"tags\":[]}";
        var result = mvc.perform(post("/api/v1/admin/tools").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() { return user("admin").roles("ADMIN"); }
}
