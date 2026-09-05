package io.haoblog.shared.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RequestBoundaryFilterTest {
    private final RequestBoundaryFilter filter = new RequestBoundaryFilter(
            new ProblemResponseWriter(JsonMapper.builder().build()));

    @Test
    void rejectsDeclaredAndChunkedBodiesWithoutLeakingContent() throws Exception {
        var declared = request("POST", "/api/v1/public/articles/a/comments", RequestBoundaryFilter.JSON_BODY_LIMIT + 1);
        declared.setContent("secret-password".repeat(30_000).getBytes(StandardCharsets.UTF_8));
        var declaredResponse = new MockHttpServletResponse();
        filter.doFilter(declared, declaredResponse, (request, response) -> fail("oversized body reached controller"));
        assertEquals(413, declaredResponse.getStatus());
        assertEquals("no-store", declaredResponse.getHeader("Cache-Control"));
        assertTrue(declaredResponse.getContentAsString().contains("REQUEST_BODY_TOO_LARGE"));
        assertFalse(declaredResponse.getContentAsString().contains("secret-password"));

        var chunked = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        chunked.setMethod("POST");
        chunked.setRequestURI("/api/v1/public/articles/a/comments");
        chunked.setContentType("Application/JSON; charset=UTF-8");
        chunked.setContent(new byte[RequestBoundaryFilter.JSON_BODY_LIMIT + 1]);
        var chunkedResponse = new MockHttpServletResponse();
        filter.doFilter(chunked, chunkedResponse, (request, response) -> request.getInputStream().readAllBytes());
        assertEquals(413, chunkedResponse.getStatus());
    }

    @Test
    void acceptsWorstCaseEscapedOneMiBMarkdownWithinArticleWireLimit() throws Exception {
        String body = "{\"markdown\":\"" + "\\u0061".repeat(1024 * 1024) + "\"}";
        var request = request("POST", "/api/v1/admin/articles", body.getBytes(StandardCharsets.UTF_8).length);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        var reached = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {
            incoming.getInputStream().readAllBytes();
            reached.set(true);
        });
        assertTrue(reached.get());
    }

    @Test
    void isolatesTwoExpensiveRequestsAndReleasesPermits() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = blockedSearch(executor, entered, release);
            var second = blockedSearch(executor, entered, release);
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            var saturatedResponse = new MockHttpServletResponse();
            filter.doFilter(request("GET", "/api/v1/public/search/articles", 0), saturatedResponse,
                    (request, response) -> fail("saturated request entered controller"));
            assertEquals(503, saturatedResponse.getStatus());
            assertEquals("1", saturatedResponse.getHeader("Retry-After"));
            assertEquals("application/problem+json", saturatedResponse.getContentType());
            assertEquals("no-store", saturatedResponse.getHeader("Cache-Control"));
            assertTrue(saturatedResponse.getContentAsString().contains("SERVICE_CAPACITY_EXHAUSTED"));

            release.countDown();
            CompletableFuture.allOf(first, second).get(2, TimeUnit.SECONDS);
        }

        var reached = new AtomicBoolean();
        filter.doFilter(request("GET", "/api/v1/public/garden", 0), new MockHttpServletResponse(),
                (request, response) -> reached.set(true));
        assertTrue(reached.get());
    }

    @Test
    void releasesExpensivePermitsAfterTimeoutOrDisconnectExceptions() throws Exception {
        assertThrows(IOException.class, () -> filter.doFilter(
                request("GET", "/api/v1/public/garden", 0), new MockHttpServletResponse(),
                (request, response) -> { throw new IOException("client disconnected"); }));
        assertThrows(ServletException.class, () -> filter.doFilter(
                request("GET", "/api/v1/public/search/articles", 0), new MockHttpServletResponse(),
                (request, response) -> { throw new ServletException("request timed out"); }));

        var reached = new AtomicBoolean();
        filter.doFilter(request("GET", "/api/v1/public/garden", 0), new MockHttpServletResponse(),
                (request, response) -> reached.set(true));
        assertTrue(reached.get());
    }

    private CompletableFuture<Void> blockedSearch(java.util.concurrent.Executor executor, CountDownLatch entered,
                                                   CountDownLatch release) {
        return CompletableFuture.runAsync(() -> {
            try {
                filter.doFilter(request("GET", "/api/v1/public/search/articles", 0), new MockHttpServletResponse(),
                        (request, response) -> {
                            entered.countDown();
                            try {
                                if (!release.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timeout");
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                            }
                        });
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, executor);
    }

    private static MockHttpServletRequest request(String method, String path, int contentLength) {
        var request = new MockHttpServletRequest(method, path);
        request.setContentType("application/json");
        if (contentLength > 0) request.setContent(new byte[contentLength]);
        return request;
    }
}
