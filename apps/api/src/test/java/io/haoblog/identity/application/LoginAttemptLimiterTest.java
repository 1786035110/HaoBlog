package io.haoblog.identity.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {
    private final AtomicReference<Instant> currentTime = new AtomicReference<>(Instant.parse("2026-08-14T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return currentTime.get(); }
    };

    @Test
    void allowsFiveFailuresAndLimitsTheSixthRequest() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.check("source-a").allowed());
            limiter.recordFailure("source-a");
        }

        var decision = limiter.check("source-a");
        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() > 0);
        assertTrue(decision.retryAfterSeconds() <= 900);
    }

    @Test
    void isolatesSourcesClearsOnSuccessAndResetsAfterWindow() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
        for (int i = 0; i < 5; i++) limiter.recordFailure("source-a");
        assertTrue(limiter.check("source-b").allowed());

        limiter.clear("source-a");
        assertTrue(limiter.check("source-a").allowed());
        for (int i = 0; i < 5; i++) limiter.recordFailure("source-a");
        currentTime.set(currentTime.get().plusSeconds(901));
        assertTrue(limiter.check("source-a").allowed());
    }

    @Test
    void capsTheNumberOfCachedSources() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
        for (int i = 0; i < 4097; i++) limiter.recordFailure("source-" + i);

        assertEquals(4096, limiter.cacheSize());
    }
}
