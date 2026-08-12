package io.haoblog.content.web;

import io.haoblog.content.application.ArticleService;
import io.haoblog.content.domain.Article;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/articles")
public class PublicArticleController {
    private final ArticleService service;
    public PublicArticleController(ArticleService service) { this.service = service; }
    @GetMapping
    public ArticleListResponse articles(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        var result = service.list(page, size).page();
        return new ArticleListResponse(result.getContent().stream().map(ArticleSummary::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }
    public record ArticleSummary(UUID id, String slug, String title, String excerpt, Instant publishedAt) {
        static ArticleSummary from(Article article) { return new ArticleSummary(article.getId(), article.getSlug(), article.getTitle(), article.getExcerpt(), article.getPublishedAt()); }
    }
    public record ArticleListResponse(List<ArticleSummary> items, int page, int size, long total) {}
}
