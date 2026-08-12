package io.haoblog.content.application;

import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class ArticleService {
    private final ArticleRepository repository;
    private final Clock clock;
    public ArticleService(ArticleRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    public PageResult list(int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("page/size out of range");
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<io.haoblog.content.domain.Article> result = repository.findByStatusAndPublishedAtIsNotNullAndPublishedAtLessThanEqual(
                ArticleStatus.PUBLISHED, Instant.now(clock), pageable);
        return new PageResult(result);
    }
    public record PageResult(Page<io.haoblog.content.domain.Article> page) {}
}
