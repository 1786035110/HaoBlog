package io.haoblog.content.application;

import io.haoblog.content.domain.ArticleStatus;
import io.haoblog.content.persistence.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledArticlePublisherTest {
    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    private final ArticleRepository articles = mock(ArticleRepository.class);
    private final ArticleWorkflowService workflow = mock(ArticleWorkflowService.class);
    private final ArticleRepository.ScheduledPublicationProjection due = candidate(NOW);
    private final ArticleRepository.ScheduledPublicationProjection future = candidate(NOW.plusSeconds(1));
    private ScheduledArticlePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ScheduledArticlePublisher(articles, workflow, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesOnlyCandidatesDueAtClockInstant() {
        when(articles.findDueScheduled(eq(ArticleStatus.SCHEDULED), eq(NOW), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(due, future));

        publisher.publishDueBatch();

        verify(workflow).publish(due.getId(), due.getVersion());
    }

    @Test
    void firstScanAfterDowntimePublishesPastDueArticle() {
        var pastDue = candidate(NOW.minusSeconds(1));
        when(articles.findDueScheduled(eq(ArticleStatus.SCHEDULED), eq(NOW), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(pastDue));

        publisher.publishDueBatch();

        verify(workflow).publish(pastDue.getId(), pastDue.getVersion());
    }

    @Test
    void oneFailureDoesNotBlockTheRestOfTheBatch() {
        var other = candidate(NOW);
        UUID dueId = due.getId();
        long dueVersion = due.getVersion();
        when(articles.findDueScheduled(eq(ArticleStatus.SCHEDULED), eq(NOW), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(due, other));
        doThrow(new IllegalStateException("test failure"))
                .when(workflow).publish(dueId, dueVersion);

        assertDoesNotThrow(() -> publisher.publishDueBatch());

        verify(workflow).publish(due.getId(), due.getVersion());
        verify(workflow).publish(other.getId(), other.getVersion());
    }

    @Test
    void repeatedScanUsesTheNextBoundedQueryAndDoesNotRepublishCompletedArticle() {
        when(articles.findDueScheduled(eq(ArticleStatus.SCHEDULED), eq(NOW), eq(PageRequest.of(0, 20))))
                .thenReturn(List.of(due), List.of());

        publisher.publishDueBatch();
        publisher.publishDueBatch();

        verify(workflow).publish(due.getId(), due.getVersion());
        verify(articles, org.mockito.Mockito.times(2))
                .findDueScheduled(eq(ArticleStatus.SCHEDULED), eq(NOW), eq(PageRequest.of(0, 20)));
    }

    private static ArticleRepository.ScheduledPublicationProjection candidate(Instant scheduledAt) {
        var candidate = mock(ArticleRepository.ScheduledPublicationProjection.class);
        when(candidate.getId()).thenReturn(UUID.randomUUID());
        when(candidate.getVersion()).thenReturn(0L);
        when(candidate.getScheduledAt()).thenReturn(scheduledAt);
        return candidate;
    }
}
