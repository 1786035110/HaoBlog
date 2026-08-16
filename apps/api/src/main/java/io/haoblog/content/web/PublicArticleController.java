package io.haoblog.content.web;

import io.haoblog.content.application.ArticleService;
import io.haoblog.content.domain.ArticleRevision;
import io.haoblog.shared.web.ProblemResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/articles")
public class PublicArticleController {
    private static final MediaType PROBLEM = MediaType.valueOf("application/problem+json");
    private final ArticleService service;

    public PublicArticleController(ArticleService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ArticleListResponse> articles(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        var result = service.list(page, size).page();
        var response = new ArticleListResponse(result.getContent().stream().map(ArticleSummary::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
        return withCache(response, representationHash(response), ifNoneMatch);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ArticleResponse> article(@PathVariable String slug,
                                                   @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ArticleRevision article = service.findPublicBySlug(slug).orElseThrow(() -> new ArticleNotFoundException(slug));
        var response = ArticleResponse.from(article);
        return withCache(response, representationHash(response), ifNoneMatch);
    }

    @ExceptionHandler(ArticleNotFoundException.class)
    ResponseEntity<ProblemResponse> articleNotFound(ArticleNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(PROBLEM)
                .body(new ProblemResponse("ARTICLE_NOT_FOUND", "Article not found", "The requested public article does not exist", MDC.get("traceId")));
    }

    private <T> ResponseEntity<T> withCache(T body, String etag, String ifNoneMatch) {
        var headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl("public, max-age=0, s-maxage=60, stale-while-revalidate=300");
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static String representationHash(Object body) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return '"' + HexFormat.of().formatHex(digest.digest(body.toString().getBytes(StandardCharsets.UTF_8))) + '"';
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create article representation ETag", exception);
        }
    }

    public record ArticleSummary(UUID id, String slug, String title, String excerpt, Instant publishedAt) {
        static ArticleSummary from(ArticleRevision article) { return new ArticleSummary(article.getArticleId(), article.getSlug(), article.getTitle(), article.getExcerpt(), article.getCreatedAt()); }
    }
    public record ArticleListResponse(List<ArticleSummary> items, int page, int size, long total) {}
    public record ArticleResponse(UUID id, String slug, String title, String excerpt, Instant publishedAt, String markdown) {
        static ArticleResponse from(ArticleRevision article) { return new ArticleResponse(article.getArticleId(), article.getSlug(), article.getTitle(), article.getExcerpt(), article.getCreatedAt(), article.getMarkdownSource()); }
    }
}
