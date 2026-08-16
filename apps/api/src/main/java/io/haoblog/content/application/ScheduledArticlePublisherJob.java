package io.haoblog.content.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledArticlePublisherJob {
    private final ScheduledArticlePublisher publisher;

    public ScheduledArticlePublisherJob(ScheduledArticlePublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 0)
    public void publishDueArticles() {
        publisher.publishDueBatch();
    }
}
