ALTER TABLE admin_user
    DROP COLUMN failed_login_attempts,
    DROP COLUMN locked_until;
