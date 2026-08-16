package io.haoblog.content.application;

import io.haoblog.content.domain.ArticleRevision;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRevisionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

@Service
public class ArticleService {
    private final ArticleRevisionRepository repository;
    private final Clock clock;
    public ArticleService(ArticleRevisionRepository repository, Clock clock) {
        this.repository = repository;
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
    public record PageResult(Page<ArticleRevision> page) {}
}
