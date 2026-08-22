package io.haoblog.content.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledArticlePublisherJob {
    private final ScheduledArticlePublisher publisher;

    public ScheduledArticlePublisherJob(ScheduledArticlePublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(
            fixedDelayString = "${HAOBLOG_CONTENT_SCHEDULING_FIXED_DELAY_MS:60000}",
            initialDelayString = "${HAOBLOG_CONTENT_SCHEDULING_INITIAL_DELAY_MS:0}"
    )
    public void publishDueArticles() {
        publisher.publishDueBatch();
    }
}
