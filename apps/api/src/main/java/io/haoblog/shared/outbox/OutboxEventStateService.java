package io.haoblog.shared.outbox;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxEventStateService {
    public static final int MAX_ATTEMPTS = 5;
    public static final int BATCH_SIZE = 1;
    private static final Duration PROCESSING_LEASE = Duration.ofSeconds(60);

    private final OutboxEventRepository repository;
    private final Clock clock;

    public OutboxEventStateService(OutboxEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public List<OutboxEvent> claimCommentCreatedBatch() {
        Instant now = clock.instant();
        List<OutboxEvent> events = repository.findAvailable("COMMENT_CREATED",
                List.of(OutboxStatus.PENDING, OutboxStatus.PROCESSING), now,
                PageRequest.of(0, BATCH_SIZE));
        return events.stream().filter(event -> {
            if (event.getAttemptCount() >= MAX_ATTEMPTS) {
                event.retryOrFail(now, now, MAX_ATTEMPTS);
                return false;
            }
            event.claim(now, now.plus(PROCESSING_LEASE));
            return true;
        }).toList();
    }

    @Transactional
    public void markProcessed(UUID eventId, int attempt) {
        repository.findByIdForUpdate(eventId).ifPresent(event -> {
            if (event.getStatus() == OutboxStatus.PROCESSING && event.getAttemptCount() == attempt) {
                event.markProcessed(clock.instant());
            }
        });
    }

    @Transactional
    public void markFailedOrRetry(UUID eventId, int attempt) {
        repository.findByIdForUpdate(eventId).ifPresent(event -> {
            if (event.getStatus() != OutboxStatus.PROCESSING || event.getAttemptCount() != attempt) return;
            Instant now = clock.instant();
            event.retryOrFail(now, now.plus(retryDelay(event.getAttemptCount())), MAX_ATTEMPTS);
        });
    }

    static Duration retryDelay(int attemptCount) {
        long seconds = Math.min(120L, 10L << Math.max(0, attemptCount - 1));
        return Duration.ofSeconds(seconds);
    }
}
