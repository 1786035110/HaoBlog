package io.haoblog.shared.web;

public record ProblemResponse(String code, String title, String detail, String traceId) {}
