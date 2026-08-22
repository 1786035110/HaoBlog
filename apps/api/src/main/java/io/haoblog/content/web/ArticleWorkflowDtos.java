package io.haoblog.content.web;

import io.haoblog.content.application.ArticleWorkflowService;
import io.haoblog.content.domain.Article;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class ArticleWorkflowDtos {
    private ArticleWorkflowDtos() {}

    public record VersionRequest(@NotNull Long version) {}

    public record ScheduleRequest(@NotNull Long version, @NotNull Instant scheduledAt) {}

    public record PreviewTokenResponse(UUID id, String token, long articleVersion, Instant expiresAt) {
        static PreviewTokenResponse from(ArticleWorkflowService.PreviewTokenResult result) {
            return new PreviewTokenResponse(result.id(), result.token(), result.articleVersion(), result.expiresAt());
        }
    }

    public record ActionResponse(UUID id, String slug, String title, io.haoblog.content.domain.ArticleStatus status,
                                 Instant publishedAt, Instant scheduledAt, long version) {
        static ActionResponse from(Article article) {
            return new ActionResponse(article.getId(), article.getSlug(), article.getTitle(), article.getStatus(),
                    article.getPublishedAt(), article.getScheduledAt(), article.getVersion());
        }
    }
}
