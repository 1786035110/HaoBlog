package io.haoblog.content.application;

import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRepository;
import io.haoblog.shared.web.ProblemException;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ScheduledArticlePublisher {
    static final int BATCH_SIZE = 20;

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledArticlePublisher.class);

    private final ArticleRepository articles;
    private final ArticleWorkflowService workflow;
    private final Clock clock;

    public ScheduledArticlePublisher(ArticleRepository articles, ArticleWorkflowService workflow, Clock clock) {
        this.articles = articles;
        this.workflow = workflow;
        this.clock = clock;
    }

    public void publishDueBatch() {
        Instant now = Instant.now(clock);
        var candidates = articles.findDueScheduled(ArticleStatus.SCHEDULED, now, PageRequest.of(0, BATCH_SIZE));
        for (var candidate : candidates) {
            if (candidate.getScheduledAt() == null || candidate.getScheduledAt().isAfter(now)) {
                continue;
            }
            publishOne(candidate.getId(), candidate.getVersion());
        }
    }

    private void publishOne(UUID articleId, long version) {
        String traceId = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId)) {
            workflow.publish(articleId, version);
        } catch (Exception exception) {
            LOG.error("定时发布失败 articleId={} traceId={} reason={}",
                    articleId, traceId, failureReason(exception));
        }
    }

    private static String failureReason(Exception exception) {
        if (exception instanceof ProblemException problem) {
            return problem.getCode() + ": " + problem.getMessage();
        }
        return exception.getClass().getSimpleName();
    }
}
