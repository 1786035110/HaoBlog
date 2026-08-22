package io.haoblog.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from OutboxEvent e
            where e.eventType = :eventType
              and e.status in :statuses
              and e.availableAt <= :now
            order by e.availableAt asc, e.createdAt asc, e.id asc
            """)
    List<OutboxEvent> findAvailable(@Param("eventType") String eventType,
                                    @Param("statuses") List<OutboxStatus> statuses,
                                    @Param("now") Instant now,
                                    Pageable pageable);
}
