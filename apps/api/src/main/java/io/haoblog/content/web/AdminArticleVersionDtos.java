package io.haoblog.content.web;

import io.haoblog.content.application.AdminContentService;
import io.haoblog.content.domain.ArticleRevision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminArticleVersionDtos {
    private AdminArticleVersionDtos() {}

    public record VersionListResponse(List<VersionSummary> items, int page, int size, long total) {}

    public record VersionSummary(UUID id, long sourceArticleVersion, Instant createdAt, String changeReason,
                                 UUID createdBy, boolean currentPublished) {
        static VersionSummary from(AdminContentService.RevisionSummary summary) {
            return new VersionSummary(summary.id(), summary.sourceArticleVersion(), summary.createdAt(),
                    summary.changeReason(), summary.createdBy(), summary.currentPublished());
        }
    }

    public record VersionResponse(UUID id, UUID articleId, long sourceArticleVersion, String title, String slug,
                                  String excerpt, String markdown, String seoTitle, String seoDescription,
                                  UUID coverMediaId, Map<String, String> categorySnapshot,
                                  List<Map<String, String>> tagSnapshot, String changeReason, UUID createdBy,
                                  Instant createdAt) {
        static VersionResponse from(ArticleRevision revision) {
            return new VersionResponse(revision.getId(), revision.getArticleId(), revision.getSourceVersion(),
                    revision.getTitle(), revision.getSlug(), revision.getExcerpt(), revision.getMarkdownSource(),
                    revision.getSeoTitle(), revision.getSeoDescription(), revision.getCoverMediaId(),
                    revision.getCategorySnapshot(), revision.getTagSnapshot(), revision.getChangeReason(),
                    revision.getCreatedBy(), revision.getCreatedAt());
        }
    }
}
