package io.haoblog.content.web;

import io.haoblog.content.application.ArticleWorkflowService;
import io.haoblog.content.domain.Article;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/article-previews")
public class PublicPreviewController {
    private final ArticleWorkflowService service;

    public PublicPreviewController(ArticleWorkflowService service) { this.service = service; }

    @GetMapping("/{token}")
    public ResponseEntity<PreviewResponse> preview(@PathVariable String token) {
        Article article = service.preview(token);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("Referrer-Policy", "no-referrer")
                .body(PreviewResponse.from(article));
    }

    public record PreviewResponse(UUID id, String slug, String title, String excerpt, String markdown,
                                  String seoTitle, String seoDescription, UUID categoryId,
                                  UUID coverMediaId, List<UUID> tagIds, long version) {
        static PreviewResponse from(Article article) {
            return new PreviewResponse(article.getId(), article.getSlug(), article.getTitle(), article.getExcerpt(),
                    article.getMarkdownSource(), article.getSeoTitle(), article.getSeoDescription(), article.getCategoryId(),
                    article.getCoverMediaId(), article.getTags().stream().map(tag -> tag.getId()).toList(), article.getVersion());
        }
    }
}
