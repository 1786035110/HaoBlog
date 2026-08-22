package io.haoblog.content.application;

public class DuplicateArticleSlugException extends RuntimeException {
    public DuplicateArticleSlugException(String slug) {
        super("Article slug is already in use: " + slug);
    }
}
