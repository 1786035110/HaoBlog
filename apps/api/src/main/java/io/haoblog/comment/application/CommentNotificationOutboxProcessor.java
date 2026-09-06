package io.haoblog.comment.application;

import io.haoblog.shared.outbox.OutboxEvent;
import io.haoblog.shared.outbox.OutboxEventStateService;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class CommentNotificationOutboxProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(CommentNotificationOutboxProcessor.class);
    private static final AtomicLong LAST_DATABASE_WARNING = new AtomicLong();

    private final OutboxEventStateService state;
    private final CommentNotificationMailer mailer;
    private final CommentNotificationProperties properties;

    public CommentNotificationOutboxProcessor(OutboxEventStateService state, CommentNotificationMailer mailer,
                                              CommentNotificationProperties properties) {
        this.state = state;
        this.mailer = mailer;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${HAOBLOG_COMMENT_NOTIFICATION_FIXED_DELAY_MS:1000}",
            initialDelayString = "${HAOBLOG_COMMENT_NOTIFICATION_INITIAL_DELAY_MS:5000}"
    )
    public void processDueBatch() {
        try {
            for (OutboxEvent event : state.claimCommentCreatedBatch()) {
                processOne(event);
            }
        } catch (DataAccessException | CannotCreateTransactionException failure) {
            warnDatabaseUnavailable();
        }
    }

    private static void warnDatabaseUnavailable() {
        long now = System.currentTimeMillis();
        long previous = LAST_DATABASE_WARNING.get();
        if (now - previous >= 60_000 && LAST_DATABASE_WARNING.compareAndSet(previous, now)) {
            LOG.warn("评论通知轮询暂停：数据库暂不可用");
        }
    }

    private void processOne(OutboxEvent event) {
        UUID traceId = UUID.randomUUID();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId.toString())) {
            if (properties.isEnabled()) {
                mailer.send(event.getAggregateId());
            }
            state.markProcessed(event.getId(), event.getAttemptCount());
            LOG.info("评论通知已处理 eventId={} commentId={} traceId={}",
                    event.getId(), event.getAggregateId(), traceId);
        } catch (Exception ignored) {
            try {
                state.markFailedOrRetry(event.getId(), event.getAttemptCount());
            } catch (Exception stateFailure) {
                LOG.warn("评论通知状态更新失败 eventId={} commentId={} traceId={}",
                        event.getId(), event.getAggregateId(), traceId);
            }
            LOG.warn("评论通知失败 eventId={} commentId={} traceId={}",
                    event.getId(), event.getAggregateId(), traceId);
        }
    }
}
