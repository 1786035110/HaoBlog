package io.haoblog.content.application;

import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRepository;
import io.haoblog.shared.web.ProblemException;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ScheduledArticlePublisher {
    static final int BATCH_SIZE = 20;

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledArticlePublisher.class);
    private static final AtomicLong LAST_DATABASE_WARNING = new AtomicLong();

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
        var candidates = findCandidates(now);
        for (var candidate : candidates) {
            if (candidate.getScheduledAt() == null || candidate.getScheduledAt().isAfter(now)) {
                continue;
            }
            publishOne(candidate.getId(), candidate.getVersion());
        }
    }

    private java.util.List<ArticleRepository.ScheduledPublicationProjection> findCandidates(Instant now) {
        try {
            return articles.findDueScheduled(ArticleStatus.SCHEDULED, now, PageRequest.of(0, BATCH_SIZE));
        } catch (DataAccessException | CannotCreateTransactionException failure) {
            long current = System.currentTimeMillis();
            long previous = LAST_DATABASE_WARNING.get();
            if (current - previous >= 60_000 && LAST_DATABASE_WARNING.compareAndSet(previous, current)) {
                LOG.warn("定时发布轮询暂停：数据库暂不可用");
            }
            return java.util.List.of();
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
