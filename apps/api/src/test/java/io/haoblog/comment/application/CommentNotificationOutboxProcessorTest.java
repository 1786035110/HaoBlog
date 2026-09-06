package io.haoblog.comment.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.haoblog.shared.outbox.OutboxEvent;
import io.haoblog.shared.outbox.OutboxEventRepository;
import io.haoblog.shared.outbox.OutboxEventStateService;
import io.haoblog.shared.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentNotificationOutboxProcessorTest {
    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock OutboxEventRepository repository;
    @Mock CommentNotificationMailer mailer;

    @Test
    void successMarksProcessed() {
        OutboxEvent event = event();
        when(repository.findAvailable(any(), any(), any(), any())).thenReturn(List.of(event));
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        processor(true).processDueBatch();

        verify(mailer).send(event.getAggregateId());
        assertEquals(OutboxStatus.PROCESSED, event.getStatus());
        assertEquals(1, event.getAttemptCount());
    }

    @Test
    void smtpFailureIsRetriedAndDoesNotBlockOtherEvents() {
        OutboxEvent failed = event();
        OutboxEvent successful = new OutboxEvent(UUID.randomUUID(), "COMMENT_CREATED", payload(), NOW, NOW);
        when(repository.findAvailable(any(), any(), any(), any())).thenReturn(List.of(failed, successful));
        when(repository.findByIdForUpdate(failed.getId())).thenReturn(Optional.of(failed));
        when(repository.findByIdForUpdate(successful.getId())).thenReturn(Optional.of(successful));
        doThrow(new CommentNotificationException()).when(mailer).send(failed.getAggregateId());

        processor(true).processDueBatch();

        verify(mailer).send(failed.getAggregateId());
        verify(mailer).send(successful.getAggregateId());
        assertEquals(OutboxStatus.PENDING, failed.getStatus());
        assertEquals(OutboxStatus.PROCESSED, successful.getStatus());
    }

    @Test
    void fifthFailureIsTerminal() {
        OutboxEvent event = event();
        when(repository.findAvailable(any(), any(), any(), any())).thenReturn(List.of(event));
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        doThrow(new CommentNotificationException()).when(mailer).send(event.getAggregateId());

        for (int attempt = 0; attempt < OutboxEventStateService.MAX_ATTEMPTS; attempt++) {
            processor(true).processDueBatch();
        }

        verify(mailer, times(OutboxEventStateService.MAX_ATTEMPTS)).send(event.getAggregateId());
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(OutboxEventStateService.MAX_ATTEMPTS, event.getAttemptCount());
    }

    @Test
    void disabledNotificationDrainsWithoutSending() {
        OutboxEvent event = event();
        when(repository.findAvailable(any(), any(), any(), any())).thenReturn(List.of(event));
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        processor(false).processDueBatch();

        verify(mailer, never()).send(event.getAggregateId());
        assertEquals(OutboxStatus.PROCESSED, event.getStatus());
    }

    @Test
    void processedEventsAreNotClaimedAgain() {
        when(repository.findAvailable(any(), any(), any(), any())).thenReturn(List.of());

        processor(true).processDueBatch();

        verify(mailer, never()).send(any());
    }

    @Test
    void claimsOnlyOneCommentAtATime() {
        when(repository.findAvailable(any(), any(), any(), any())).thenReturn(List.of());

        processor(true).processDueBatch();

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAvailable(org.mockito.ArgumentMatchers.eq("COMMENT_CREATED"), any(), any(), captor.capture());
        assertEquals(1, captor.getValue().getPageSize());
    }

    @Test
    void failureLogsDoNotContainSensitiveExceptionDetails() {
        OutboxEvent event = event();
        when(repository.findAvailable(any(), any(), any(), any())).thenReturn(List.of(event));
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        doThrow(new IllegalStateException("smtp-password owner@example.invalid comment body"))
                .when(mailer).send(event.getAggregateId());

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(CommentNotificationOutboxProcessor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            processor(true).processDueBatch();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + b);
        assertFalse(logs.contains("smtp-password"));
        assertFalse(logs.contains("owner@example.invalid"));
        assertFalse(logs.contains("comment body"));
    }

    @Test
    void databaseOutageDoesNotEscapeScheduledPoller() {
        when(repository.findAvailable(any(), any(), any(), any()))
                .thenThrow(new CannotCreateTransactionException("synthetic database outage"));

        assertDoesNotThrow(() -> processor(true).processDueBatch());
    }

    private CommentNotificationOutboxProcessor processor(boolean enabled) {
        CommentNotificationProperties properties = new CommentNotificationProperties();
        properties.setEnabled(enabled);
        return new CommentNotificationOutboxProcessor(
                new OutboxEventStateService(repository, CLOCK), mailer, properties);
    }

    private static OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), "COMMENT_CREATED", payload(), NOW, NOW);
    }

    private static Map<String, Object> payload() {
        return Map.of("commentId", UUID.randomUUID().toString(), "articleId", UUID.randomUUID().toString(),
                "eventType", "COMMENT_CREATED", "occurredAt", NOW.toString());
    }
}
