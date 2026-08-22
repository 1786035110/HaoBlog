package io.haoblog.shared.web;

public class ProblemException extends RuntimeException {
    private final String code;
    private final String title;
    private final Long currentVersion;

    public ProblemException(String code, String title, String detail) {
        this(code, title, detail, null);
    }

    public ProblemException(String code, String title, String detail, Long currentVersion) {
        super(detail);
        this.code = code;
        this.title = title;
        this.currentVersion = currentVersion;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
    public Long getCurrentVersion() { return currentVersion; }
}
