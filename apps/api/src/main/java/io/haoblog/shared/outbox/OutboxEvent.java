package io.haoblog.shared.outbox;

import io.haoblog.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    private UUID id = UuidV7.generate();
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status = OutboxStatus.PENDING;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "available_at", nullable = false)
    private Instant availableAt;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEvent() {}

    public OutboxEvent(UUID aggregateId, String eventType, Map<String, Object> payload,
                       Instant availableAt, Instant createdAt) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = Map.copyOf(payload);
        this.availableAt = availableAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void claim(Instant now, Instant leaseUntil) {
        if (status != OutboxStatus.PENDING && status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException("Only pending outbox events can be claimed");
        }
        if (now == null || leaseUntil == null || leaseUntil.isBefore(now)) {
            throw new IllegalArgumentException("Outbox claim times are invalid");
        }
        status = OutboxStatus.PROCESSING;
        attemptCount++;
        availableAt = leaseUntil;
    }

    public void markProcessed(Instant now) {
        if (status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException("Only processing outbox events can be processed");
        }
        status = OutboxStatus.PROCESSED;
        processedAt = now;
    }

    public void retryOrFail(Instant now, Instant nextRetry, int maxAttempts) {
        if (status != OutboxStatus.PROCESSING) {
            throw new IllegalStateException("Only processing outbox events can be retried");
        }
        if (attemptCount >= maxAttempts) {
            status = OutboxStatus.FAILED;
            availableAt = now;
            return;
        }
        status = OutboxStatus.PENDING;
        availableAt = nextRetry;
    }
}
