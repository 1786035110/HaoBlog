package io.haoblog.content.web;

import io.haoblog.content.application.ArticleService;
import io.haoblog.site.application.SiteService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/public/search")
public class PublicSearchController {
    private final ArticleService service;
    private final SiteService siteService;

    public PublicSearchController(ArticleService service, SiteService siteService) {
        this.service = service;
        this.siteService = siteService;
    }

    @GetMapping("/articles")
    public ResponseEntity<PublicArticleController.ArticleListResponse> articles(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        var result = service.search(q, page, size);
        var searchPage = result.page();
        Map<UUID, String> coverUrls = service.publicCoverUrls(searchPage.getContent().stream()
                .map(ArticleService.PublicSearchArticle::coverMediaId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));
        var site = siteService.get();
        boolean globalCommentsEnabled = site == null || site.commentsEnabled();
        var response = new PublicArticleController.ArticleListResponse(
                searchPage.getContent().stream().map(article -> new PublicArticleController.ArticleSummary(
                        article.id(), article.slug(), article.title(), article.excerpt(), article.publishedAt(),
                        article.coverMediaId() == null ? null : coverUrls.get(article.coverMediaId()),
                        article.commentsEnabled() && globalCommentsEnabled)).toList(),
                searchPage.getNumber(), searchPage.getSize(), searchPage.getTotalElements());
        return withCache(response, representationHash("search", result.query(), response), ifNoneMatch);
    }

    private <T> ResponseEntity<T> withCache(T body, String etag, String ifNoneMatch) {
        var headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl("public, max-age=0, s-maxage=60, must-revalidate");
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(304).headers(headers).build();
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private static String representationHash(Object... values) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            String stableInput = java.util.Arrays.deepToString(values);
            return '"' + HexFormat.of().formatHex(digest.digest(stableInput.getBytes(StandardCharsets.UTF_8))) + '"';
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create article search representation ETag", exception);
        }
    }
}
