package io.haoblog.shared.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBoundaryFilter extends OncePerRequestFilter {
    static final int JSON_BODY_LIMIT = 256 * 1024;
    static final int ARTICLE_BODY_LIMIT = 8 * 1024 * 1024;
    private static final Set<String> PAYLOAD_METHODS = Set.of("POST", "PUT", "PATCH");
    private final Semaphore expensiveRequests = new Semaphore(2, true);
    private final ProblemResponseWriter problems;

    public RequestBoundaryFilter(ProblemResponseWriter problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean gated = isExpensive(request);
        if (gated && !expensiveRequests.tryAcquire()) {
            response.setHeader("Retry-After", "1");
            problems.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVICE_CAPACITY_EXHAUSTED", "Service capacity exhausted", "Retry the request later");
            return;
        }
        try {
            int limit = bodyLimit(request);
            if (limit > 0 && request.getContentLengthLong() > limit) {
                writeTooLarge(request, response);
                return;
            }
            chain.doFilter(limit > 0 ? new LimitedRequest(request, limit) : request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            if (!hasTooLargeCause(exception) || response.isCommitted()) throw exception;
            writeTooLarge(request, response);
        } finally {
            if (gated) expensiveRequests.release();
        }
    }

    private void writeTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        problems.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE,
                "REQUEST_BODY_TOO_LARGE", "Request body too large", "The request body exceeds the allowed size");
    }

    private static boolean isExpensive(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) return false;
        return Set.of("/api/v1/public/garden", "/api/v1/public/search/articles").contains(request.getRequestURI());
    }

    private static int bodyLimit(HttpServletRequest request) {
        if (!PAYLOAD_METHODS.contains(request.getMethod())) return 0;
        String contentType = request.getContentType();
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!(normalizedContentType.startsWith("application/json") || normalizedContentType.contains("+json"))) return 0;
        String path = request.getRequestURI();
        if (("POST".equals(request.getMethod()) && "/api/v1/admin/articles".equals(path))
                || ("PUT".equals(request.getMethod()) && path.matches("/api/v1/admin/articles/[^/]+"))) {
            return ARTICLE_BODY_LIMIT;
        }
        return JSON_BODY_LIMIT;
    }

    private static boolean hasTooLargeCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof RequestBodyTooLargeException) return true;
        }
        return false;
    }

    public static final class RequestBodyTooLargeException extends IOException {
        RequestBodyTooLargeException() {
            super("request body exceeds configured limit");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final int limit;

        private LimitedRequest(HttpServletRequest request, int limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedInputStream(super.getInputStream(), limit);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final int limit;
        private int read;

        private LimitedInputStream(ServletInputStream delegate, int limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0 && ++read > limit) throw new RequestBodyTooLargeException();
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int value = delegate.read(bytes, offset, Math.min(length, limit - read + 1));
            if (value > 0 && (read += value) > limit) throw new RequestBodyTooLargeException();
            return value;
        }
    }
}
