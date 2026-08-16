package io.haoblog.content.application;

import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Configuration
@Profile({"local", "dev"})
@ConditionalOnProperty(name = "haoblog.seed.enabled", havingValue = "true", matchIfMissing = false)
public class ArticleDevelopmentSeeder {
    @Bean
    ApplicationRunner articleSeedRunner(ArticleSeedService seedService) {
        return args -> seedService.seed();
    }

    @Configuration
    static class SeedServices {
        @Bean ArticleSeedService articleSeedService(ArticleRepository repository,
                                                     ArticleWorkflowService workflowService,
                                                     Clock clock) {
            return new ArticleSeedService(repository, workflowService, clock);
        }
    }

    static class ArticleSeedService {
        private final ArticleRepository repository;
        private final ArticleWorkflowService workflowService;
        private final Clock clock;
        ArticleSeedService(ArticleRepository repository, ArticleWorkflowService workflowService, Clock clock) {
            this.repository = repository;
            this.workflowService = workflowService;
            this.clock = clock;
        }

        @Transactional
        public void seed() {
            Instant now = Instant.now(clock);
            insertIfAbsent("night-signal-baseline", "夜间信号基线", "第一个公开观测样本。", "# 夜间信号基线\n\n这是 HaoBlog 的开发观测样本。", now);
            insertIfAbsent("safe-markdown-baseline", "安全 Markdown 基线", "只保留可读、克制的基础正文。", "# 安全 Markdown 基线\n\n正文先以 SSR HTML 可读。\n\n- 原始 HTML 不执行\n- 危险链接不放行", now);
        }

        private void insertIfAbsent(String slug, String title, String excerpt, String markdown, Instant now) {
            if (repository.findBySlug(slug).isEmpty()) {
                Article article = repository.saveAndFlush(
                        new Article(slug, title, excerpt, markdown, ArticleStatus.DRAFT, null, now));
                workflowService.publish(article.getId(), article.getVersion());
            }
        }
    }
}
