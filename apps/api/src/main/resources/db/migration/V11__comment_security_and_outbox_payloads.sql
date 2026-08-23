ALTER TABLE comment
    RENAME COLUMN body TO content;

ALTER TABLE comment
    RENAME COLUMN moderated_by TO moderator_id;

ALTER TABLE comment
    ADD COLUMN email_key_version integer,
    ADD COLUMN ip_hmac_date date;

UPDATE comment
SET email_key_version = 1
WHERE email_ciphertext IS NOT NULL;

UPDATE comment
SET ip_hmac_date = (created_at AT TIME ZONE 'UTC')::date;

ALTER TABLE comment
    ALTER COLUMN ip_hmac_date SET NOT NULL,
    DROP CONSTRAINT comment_email_pair,
    ADD CONSTRAINT comment_email_pair CHECK (
        (email_ciphertext IS NULL AND email_nonce IS NULL AND email_key_version IS NULL)
        OR (email_ciphertext IS NOT NULL AND email_nonce IS NOT NULL AND email_key_version = 1)
    ),
    ADD CONSTRAINT comment_uuid_v7_check CHECK (substring(id::text, 15, 1) = '7');

ALTER TABLE outbox_event
    DROP CONSTRAINT outbox_article_payload,
    ADD CONSTRAINT outbox_event_payload_by_type CHECK (
        event_type NOT IN ('ARTICLE_PUBLISHED', 'COMMENT_CREATED')
        OR (
            event_type = 'ARTICLE_PUBLISHED'
            AND jsonb_typeof(payload) = 'object'
            AND payload->>'eventType' = 'ARTICLE_PUBLISHED'
            AND payload ?& ARRAY['articleId', 'revisionId', 'eventType', 'occurredAt']
            AND (payload - ARRAY['articleId', 'revisionId', 'eventType', 'occurredAt']) = '{}'::jsonb
        )
        OR (
            event_type = 'COMMENT_CREATED'
            AND jsonb_typeof(payload) = 'object'
            AND payload->>'eventType' = 'COMMENT_CREATED'
            AND payload ?& ARRAY['commentId', 'articleId', 'eventType', 'occurredAt']
            AND (payload - ARRAY['commentId', 'articleId', 'eventType', 'occurredAt']) = '{}'::jsonb
        )
    );

CREATE UNIQUE INDEX outbox_comment_created_uq
    ON outbox_event (aggregate_id, event_type)
    WHERE event_type = 'COMMENT_CREATED';
