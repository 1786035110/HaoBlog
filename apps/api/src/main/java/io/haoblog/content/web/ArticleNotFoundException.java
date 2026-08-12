package io.haoblog.content.web;

public class ArticleNotFoundException extends RuntimeException {
    public ArticleNotFoundException(String slug) {
        super("Public article not found: " + slug);
    }
}
