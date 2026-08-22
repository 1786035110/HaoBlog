package io.haoblog.comment.web;

import io.haoblog.comment.application.CommentChallengeService;
import io.haoblog.content.application.ArticleCommentLookup;
import io.haoblog.shared.web.ProblemResponse;
import io.haoblog.site.application.SiteService;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/public/articles")
public class PublicCommentContextController {
    private final ArticleCommentLookup articles;
    private final SiteService site;
    private final CommentChallengeService challenges;

    public PublicCommentContextController(ArticleCommentLookup articles, SiteService site, CommentChallengeService challenges) {
        this.articles = articles;
        this.site = site;
        this.challenges = challenges;
    }

    @GetMapping("/{slug}/comments/form-context")
    public FormContext formContext(@PathVariable String slug, CsrfToken csrfToken) {
        var article = articles.findPublicCommentTarget(slug).orElseThrow(() -> new ArticleNotFoundException(slug));
        var issued = challenges.issue(article.articleId());
        return new FormContext(csrfToken.getToken(), issued.token(), issued.expiresAt(),
                site.get().commentsEnabled() && article.commentsEnabled());
    }

    @ExceptionHandler(ArticleNotFoundException.class)
    ResponseEntity<ProblemResponse> articleNotFound(ArticleNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ProblemResponse("ARTICLE_NOT_FOUND", "Article not found",
                        "The requested public article does not exist", MDC.get("traceId")));
    }

    public record FormContext(String csrfToken, String challenge, Instant expiresAt, boolean commentsEnabled) {}

    private static final class ArticleNotFoundException extends RuntimeException {
        private ArticleNotFoundException(String slug) {
            super("Article not found: " + slug);
        }
    }
}
