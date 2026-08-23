CREATE INDEX outbox_comment_available_idx
    ON outbox_event (available_at, created_at, id)
    WHERE event_type = 'COMMENT_CREATED'
      AND status IN ('PENDING', 'PROCESSING');
