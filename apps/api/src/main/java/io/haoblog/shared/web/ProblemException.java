package io.haoblog.shared.web;

public class ProblemException extends RuntimeException {
    private final String code;
    private final String title;

    public ProblemException(String code, String title, String detail) {
        super(detail);
        this.code = code;
        this.title = title;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
}
