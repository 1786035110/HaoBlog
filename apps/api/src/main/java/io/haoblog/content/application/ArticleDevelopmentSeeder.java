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
            insertIfAbsent("s3-07-advanced-markdown", "S3-07 高级 Markdown 观测样本", "覆盖公式、代码、图表、表格、任务、脚注和提示块的公开阅读样本。", advancedMarkdown(), now);
        }

        private String advancedMarkdown() {
            return """
                    # S3-07 高级 Markdown 观测样本

                    这篇文章用于公开阅读的移动端、无障碍和性能验收。正文、公式和代码在 SSR HTML 中直接可读。

                    ![观测站静态封面](https://cdn.example.test/observation-cover.png)

                    ## 观测目标

                    - [x] 文章标题与正文无需 JavaScript 即可读取
                    - [x] 代码块可复制并可局部横向滚动
                    - [ ] Mermaid 在 Save-Data 下等待用户主动渲染

                    ::: note 读者提示
                    颜色不是状态的唯一表达；每个状态同时保留文字说明。
                    :::

                    ## 重复标题

                    同名章节会获得稳定且不冲突的锚点。

                    ## 重复标题

                    第二个同名章节用于验证 TOC 去重。

                    ### 数据与公式

                    行级公式 $E=mc^2$ 保持数学语义；块级公式如下：

                    $$
                    f(x) = \\int_{-\\infty}^{\\infty} e^{-t^2} \\, dt = \\sqrt{\\pi}
                    $$

                    ## 代码样本

                    ```typescript [signal.ts] {2}
                    export function signalScore(read: number, total: number) {
                      return total > 0 ? Math.min(1, read / total) : 0
                    }
                    ```

                    ## Mermaid 样本

                    ```mermaid
                    flowchart TD
                      SSR[SSR HTML] --> Readable[可读正文]
                      Readable --> Hydrate[客户端同步]
                      Hydrate --> Reduced[按偏好降级]
                    ```

                    ## 宽表格

                    | 路径 | 首屏策略 | 状态 |
                    | --- | --- | --- |
                    | 正文 | SSR 输出 | 已锁定 |
                    | Mermaid | 视口或主动请求 | 延迟 |
                    | 图片 | Save-Data 下省略 | 可降级 |

                    ::: warning 验收边界
                    表格只在自己的滚动区域内横向移动，不推动页面整体宽度。
                    :::

                    ## 脚注与安全边界

                    公开 Markdown 会经过协议与 HTML 清洗[^safety]，恶意标签不会成为可执行 DOM。

                    [危险链接](javascript:alert('blocked'))

                    [^safety]: 这是公开阅读渲染器的安全边界说明。
                    """;
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
