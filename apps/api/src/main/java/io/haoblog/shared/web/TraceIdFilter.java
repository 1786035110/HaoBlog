package io.haoblog.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-ID";
    private static final String MDC_KEY = "traceId";
    private static final Logger LOG = LoggerFactory.getLogger(TraceIdFilter.class);
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final ConcurrentHashMap<String, Long> LAST_ERROR_LOG = new ConcurrentHashMap<>();
    private final ProblemResponseWriter problemResponseWriter;

    public TraceIdFilter(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String candidate = request.getHeader(HEADER);
        String traceId = candidate != null && VALID.matcher(candidate).matches()
                ? candidate : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException exception) {
            if (response.isCommitted() || !isDatabaseUnavailable(exception)) throw exception;
            response.reset();
            response.setHeader("Retry-After", "2");
            problemResponseWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "DATABASE_BUSY", "Database temporarily unavailable",
                    "Retry later; check saved state before repeating a write");
        } finally {
            int status = response.getStatus();
            String route = routeClass(request.getRequestURI());
            String code = MDC.get("errorCode");
            String key = route + ':' + status + ':' + (code == null ? "-" : code);
            long now = System.currentTimeMillis();
            if (status < 500 || now - LAST_ERROR_LOG.getOrDefault(key, 0L) >= 10_000) {
                if (status >= 500) LAST_ERROR_LOG.put(key, now);
                LOG.info("request traceId={} route={} method={} status={} durationMs={} code={}", traceId, route,
                        request.getMethod(), status, (System.nanoTime() - started) / 1_000_000, code == null ? "-" : code);
            }
            MDC.remove("errorCode");
            MDC.remove(MDC_KEY);
        }
    }

    static String routeClass(String path) {
        if (path.startsWith("/actuator/")) return "health";
        if (path.startsWith("/api/v1/admin/")) return "admin_api";
        if (path.startsWith("/api/v1/public/")) return "public_api";
        if (path.equals("/rss.xml") || path.equals("/sitemap.xml")) return "feed";
        return "other";
    }

    private static boolean isDatabaseUnavailable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DataAccessException || current instanceof CannotCreateTransactionException) return true;
        }
        return false;
    }
}
