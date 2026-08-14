package io.haoblog.identity.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class LoginAttemptLimiter {
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_FAILURES = 5;
    private static final long MAX_CACHE_SIZE = 4096;

    private final Clock clock;
    private final Cache<String, AttemptState> attempts = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterAccess(WINDOW)
            .build();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision check(String source) {
        Instant now = clock.instant();
        AttemptState state = attempts.getIfPresent(source);
        if (state == null || !state.windowEndsAt().isAfter(now)) {
            if (state != null) {
                attempts.invalidate(source);
            }
            return Decision.permitted();
        }
        if (state.failures() < MAX_FAILURES) {
            return Decision.permitted();
        }
        long retryAfter = Math.max(1, Math.min(WINDOW.toSeconds(),
                (Duration.between(now, state.windowEndsAt()).toMillis() + 999) / 1000));
        return new Decision(false, retryAfter);
    }

    public void recordFailure(String source) {
        Instant now = clock.instant();
        attempts.asMap().compute(source, (key, state) -> {
            if (state == null || !state.windowEndsAt().isAfter(now)) {
                return new AttemptState(now, 1);
            }
            return new AttemptState(state.windowStartedAt(), state.failures() + 1);
        });
    }

    public void clear(String source) {
        attempts.invalidate(source);
    }

    long cacheSize() {
        attempts.cleanUp();
        return attempts.estimatedSize();
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision permitted() {
            return new Decision(true, 0);
        }
    }

    private record AttemptState(Instant windowStartedAt, int failures) {
        Instant windowEndsAt() {
            return windowStartedAt.plus(WINDOW);
        }
    }
}
