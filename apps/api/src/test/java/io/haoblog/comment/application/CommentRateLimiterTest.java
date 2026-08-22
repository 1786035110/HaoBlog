package io.haoblog.comment.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CommentRateLimiterTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-22T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void limitsThreeInTenMinutesThenAllowsAfterWindow() {
        CommentRateLimiter limiter = new CommentRateLimiter(clock);
        assertTrue(limiter.checkAndRecord("visitor:a").allowed());
        assertTrue(limiter.checkAndRecord("visitor:a").allowed());
        assertTrue(limiter.checkAndRecord("visitor:a").allowed());
        var limited = limiter.checkAndRecord("visitor:a");
        assertFalse(limited.allowed());
        assertTrue(limited.retryAfterSeconds() > 0);

        now.updateAndGet(value -> value.plus(Duration.ofMinutes(10).plusSeconds(1)));
        assertTrue(limiter.checkAndRecord("visitor:a").allowed());
    }

    @Test
    void limitsTenPerUtcDayAndBoundsCache() {
        CommentRateLimiter limiter = new CommentRateLimiter(clock);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.checkAndRecord("ip:a").allowed());
            now.updateAndGet(value -> value.plus(Duration.ofMinutes(11)));
        }
        var limited = limiter.checkAndRecord("ip:a");
        assertFalse(limited.allowed());
        assertTrue(limited.retryAfterSeconds() > 0);

        for (int i = 0; i < 4097; i++) limiter.checkAndRecord("source-" + i);
        assertEquals(4096, limiter.cacheSize());
    }
}
