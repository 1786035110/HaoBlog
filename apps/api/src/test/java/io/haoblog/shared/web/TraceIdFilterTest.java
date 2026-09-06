package io.haoblog.shared.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {
    private final TraceIdFilter filter = new TraceIdFilter(
            new ProblemResponseWriter(JsonMapper.builder().build()));
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Test void missingIdIsGeneratedAndMdcIsCleaned() throws Exception {
        MockHttpServletResponse response = invoke(null);
        assertTrue(VALID.matcher(response.getHeader(TraceIdFilter.HEADER)).matches());
        assertNull(MDC.get("traceId"));
    }

    @Test void validIdIsReusedAndAvailableToFilterChain() throws Exception {
        MockHttpServletResponse response = invoke("abc_-.123");
        assertEquals("abc_-.123", response.getHeader(TraceIdFilter.HEADER));
        assertNull(MDC.get("traceId"));
    }

    @Test void invalidAndOversizedIdsAreReplaced() throws Exception {
        for (String value : new String[]{"bad id", "x".repeat(65)}) {
            MockHttpServletResponse response = invoke(value);
            String actual = response.getHeader(TraceIdFilter.HEADER);
            assertTrue(VALID.matcher(actual).matches());
            assertNotEquals(value, actual);
            assertNull(MDC.get("traceId"));
        }
    }

    @Test void routesAreLoggedOnlyAsBoundedClasses() {
        assertEquals("admin_api", TraceIdFilter.routeClass("/api/v1/admin/articles/private-token"));
        assertEquals("public_api", TraceIdFilter.routeClass("/api/v1/public/articles/secret-slug"));
        assertEquals("health", TraceIdFilter.routeClass("/actuator/health/readiness"));
        assertEquals("other", TraceIdFilter.routeClass("/raw/private/path"));
    }

    @Test void databaseFailureBeforeControllerIsAServiceUnavailableProblem() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/session");
        request.addHeader(TraceIdFilter.HEADER, "db-failure-test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            throw new CannotCreateTransactionException("synthetic database outage");
        });

        assertEquals(503, response.getStatus());
        assertEquals("2", response.getHeader("Retry-After"));
        assertEquals("application/problem+json", response.getContentType());
        assertEquals("db-failure-test", response.getHeader(TraceIdFilter.HEADER));
        assertTrue(response.getContentAsString().contains("\"code\":\"DATABASE_BUSY\""));
        assertFalse(response.getContentAsString().contains("synthetic database outage"));
    }

    private MockHttpServletResponse invoke(String requestId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (requestId != null) request.addHeader(TraceIdFilter.HEADER, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertTrue(MDC.get("traceId") != null));
        return response;
    }
}
