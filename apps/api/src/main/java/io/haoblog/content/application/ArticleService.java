package io.haoblog.content.application;

import io.haoblog.content.domain.ArticleRevision;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRevisionRepository;
import io.haoblog.media.domain.MediaAsset;
import io.haoblog.media.domain.MediaAssetStatus;
import io.haoblog.media.persistence.MediaAssetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ArticleService {
    private final ArticleRevisionRepository repository;
    private final MediaAssetRepository mediaRepository;
    private final Clock clock;
    public ArticleService(ArticleRevisionRepository repository, MediaAssetRepository mediaRepository, Clock clock) {
        this.repository = repository;
        this.mediaRepository = mediaRepository;
        this.clock = clock;
    }
    public PageResult list(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("page/size out of range");
        var pageable = PageRequest.of(page, size);
        Page<ArticleRevision> result = repository.findVisible(ArticleStatus.PUBLISHED, ArticleStatus.SCHEDULED,
                java.time.Instant.now(clock), pageable);
        return new PageResult(result);
    }
    public Optional<ArticleRevision> findPublicBySlug(String slug) {
        if (slug == null || slug.isBlank()) return Optional.empty();
        return repository.findVisibleBySlug(slug, ArticleStatus.PUBLISHED, ArticleStatus.SCHEDULED,
                java.time.Instant.now(clock));
    }
    public Map<UUID, String> publicCoverUrls(Collection<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) return Map.of();
        return mediaRepository.findAllByIdInAndStatus(mediaIds, MediaAssetStatus.AVAILABLE).stream()
                .filter(asset -> asset.getMimeType() != null && asset.getMimeType().toLowerCase().startsWith("image/"))
                .filter(asset -> asset.getPublicUrl() != null && !asset.getPublicUrl().isBlank())
                .collect(Collectors.toUnmodifiableMap(MediaAsset::getId, MediaAsset::getPublicUrl));
    }
    public record PageResult(Page<ArticleRevision> page) {}
}
