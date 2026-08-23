package io.haoblog.content.web;

import io.haoblog.content.application.ArticleService;
import io.haoblog.content.domain.ArticleRevision;
import io.haoblog.shared.web.ProblemResponse;
import io.haoblog.site.application.SiteService;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/public/articles")
public class PublicArticleController {
    private static final MediaType PROBLEM = MediaType.valueOf("application/problem+json");
    private final ArticleService service;
    private final SiteService siteService;

    @Autowired
    public PublicArticleController(ArticleService service, SiteService siteService) {
        this.service = service;
        this.siteService = siteService;
    }

    public PublicArticleController(ArticleService service) {
        this(service, null);
    }

    @GetMapping
    public ResponseEntity<ArticleListResponse> articles(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        var result = service.list(page, size).page();
        Map<UUID, String> coverUrls = service.publicCoverUrls(result.getContent().stream()
                .map(article -> article.revision().getCoverMediaId()).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        boolean globalCommentsEnabled = globalCommentsEnabled();
        var response = new ArticleListResponse(result.getContent().stream().map(article -> ArticleSummary.from(article,
                        article.revision().getCoverMediaId() == null ? null : coverUrls.get(article.revision().getCoverMediaId()),
                        globalCommentsEnabled)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
        String etag = representationHash("list", result.getContent().stream().map(article -> article.revision().getId()).toList(), response);
        return withCache(response, etag, ifNoneMatch);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ArticleResponse> article(@PathVariable String slug,
                                                   @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ArticleService.PublicArticle article = service.findPublicBySlug(slug).orElseThrow(() -> new ArticleNotFoundException(slug));
        String coverImageUrl = article.revision().getCoverMediaId() == null ? null : service.publicCoverUrls(java.util.Set.of(article.revision().getCoverMediaId())).get(article.revision().getCoverMediaId());
        var response = ArticleResponse.from(article, coverImageUrl, globalCommentsEnabled());
        return withCache(response, representationHash("detail", article.revision().getId(), response), ifNoneMatch);
    }

    @ExceptionHandler(ArticleNotFoundException.class)
    ResponseEntity<ProblemResponse> articleNotFound(ArticleNotFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(PROBLEM)
                .body(new ProblemResponse("ARTICLE_NOT_FOUND", "Article not found", "The requested public article does not exist", MDC.get("traceId")));
    }

    private <T> ResponseEntity<T> withCache(T body, String etag, String ifNoneMatch) {
        var headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl("public, max-age=0, s-maxage=60, must-revalidate");
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static String representationHash(Object... values) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            String stableInput = java.util.Arrays.deepToString(values);
            return '"' + HexFormat.of().formatHex(digest.digest(stableInput.getBytes(java.nio.charset.StandardCharsets.UTF_8))) + '"';
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create article representation ETag", exception);
        }
    }

    private boolean globalCommentsEnabled() {
        if (siteService == null) return true;
        var site = siteService.get();
        return site == null || site.commentsEnabled();
    }

    public record ArticleSummary(UUID id, String slug, String title, String excerpt, Instant publishedAt, String coverImageUrl,
                                 boolean commentsEnabled) {
        static ArticleSummary from(ArticleService.PublicArticle article, String coverImageUrl, boolean globalCommentsEnabled) {
            ArticleRevision revision = article.revision();
            return new ArticleSummary(revision.getArticleId(), revision.getSlug(), revision.getTitle(), revision.getExcerpt(),
                    article.publishedAt(), coverImageUrl, article.commentsEnabled() && globalCommentsEnabled);
        }
    }
    public record ArticleListResponse(List<ArticleSummary> items, int page, int size, long total) {}
    public record ArticleResponse(UUID id, String slug, String title, String excerpt, Instant publishedAt, Instant modifiedAt,
                                  String markdown, String seoTitle, String seoDescription, String coverImageUrl,
                                  boolean commentsEnabled) {
        static ArticleResponse from(ArticleService.PublicArticle article, String coverImageUrl, boolean globalCommentsEnabled) {
            ArticleRevision revision = article.revision();
            return new ArticleResponse(revision.getArticleId(), revision.getSlug(), revision.getTitle(), revision.getExcerpt(),
                    article.publishedAt(), revision.getCreatedAt(), revision.getMarkdownSource(), revision.getSeoTitle(),
                    revision.getSeoDescription(), coverImageUrl, article.commentsEnabled() && globalCommentsEnabled);
        }
    }
}
