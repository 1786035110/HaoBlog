package io.haoblog.content.web;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminArticleDtos {
    private AdminArticleDtos() {}

    public record CreateRequest(
            @Size(max = 160) String slug,
            @Size(max = 240) String title,
            @Size(max = 600) String excerpt,
            String markdown,
            @Size(max = 240) String seoTitle,
            @Size(max = 600) String seoDescription,
            UUID categoryId,
            UUID coverMediaId,
            List<UUID> tagIds) {}

    public record UpdateRequest(
            @NotNull Long version,
            @Size(max = 160) String slug,
            @NotBlank @Size(max = 240) String title,
            @Size(max = 600) String excerpt,
            @NotNull String markdown,
            @Size(max = 240) String seoTitle,
            @Size(max = 600) String seoDescription,
            UUID categoryId,
            UUID coverMediaId,
            List<UUID> tagIds) {}

    public record ListResponse(List<Summary> items, int page, int size, long total) {}

    public record Summary(UUID id, String slug, String title, ArticleStatus status,
                          Instant updatedAt, long version) {
        static Summary from(Article article) {
            return new Summary(article.getId(), article.getSlug(), article.getTitle(), article.getStatus(),
                    article.getUpdatedAt(), article.getVersion());
        }
    }

    public record Response(UUID id, String slug, String title, String excerpt, String markdown,
                           ArticleStatus status, Instant publishedAt, Instant scheduledAt,
                           String seoTitle, String seoDescription, UUID categoryId,
                           UUID coverMediaId, List<UUID> tagIds, Instant createdAt,
                           Instant updatedAt, long version) {
        static Response from(Article article) {
            return new Response(article.getId(), article.getSlug(), article.getTitle(), article.getExcerpt(),
                    article.getMarkdownSource(), article.getStatus(), article.getPublishedAt(), article.getScheduledAt(),
                    article.getSeoTitle(), article.getSeoDescription(), article.getCategoryId(), article.getCoverMediaId(),
                    article.getTags().stream().map(tag -> tag.getId()).toList(), article.getCreatedAt(),
                    article.getUpdatedAt(), article.getVersion());
        }
    }
}
