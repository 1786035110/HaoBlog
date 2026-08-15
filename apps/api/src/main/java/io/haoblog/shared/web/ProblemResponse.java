package io.haoblog.shared.web;

public record ProblemResponse(String code, String title, String detail, String traceId, Long currentVersion) {
    public ProblemResponse(String code, String title, String detail, String traceId) {
        this(code, title, detail, traceId, null);
    }
}
