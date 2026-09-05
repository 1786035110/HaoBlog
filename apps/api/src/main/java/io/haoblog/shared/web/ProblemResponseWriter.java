package io.haoblog.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProblemResponseWriter {
    public static final MediaType PROBLEM = MediaType.valueOf("application/problem+json");
    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<ProblemResponse> response(HttpStatus status, String code, String title, String detail) {
        return response(status, code, title, detail, null);
    }

    public ResponseEntity<ProblemResponse> response(HttpStatus status, String code, String title, String detail,
                                                    Long currentVersion) {
        return ResponseEntity.status(status).contentType(PROBLEM).header("Cache-Control", "no-store")
                .body(new ProblemResponse(code, title, detail == null || detail.isBlank() ? title : detail,
                        traceId(), currentVersion));
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String code, String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(PROBLEM.toString());
        response.setHeader("Cache-Control", "no-store");
        String traceId = traceId(request);
        response.setHeader(TraceIdFilter.HEADER, traceId);
        objectMapper.writeValue(response.getOutputStream(),
                new ProblemResponse(code, title, detail == null || detail.isBlank() ? title : detail, traceId));
    }

    private String traceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private String traceId(HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String candidate = request.getHeader(TraceIdFilter.HEADER);
        return candidate != null && Pattern.matches("[A-Za-z0-9._-]{1,64}", candidate)
                ? candidate : UUID.randomUUID().toString();
    }
}
