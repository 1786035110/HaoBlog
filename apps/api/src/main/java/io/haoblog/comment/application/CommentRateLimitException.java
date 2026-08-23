package io.haoblog.comment.application;

public class CommentRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public CommentRateLimitException(long retryAfterSeconds) {
        super("Comment rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
