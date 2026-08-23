package io.haoblog.comment.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
public class CommentRateLimiter {
    static final Duration SHORT_WINDOW = Duration.ofMinutes(10);
    static final int SHORT_LIMIT = 3;
    static final int DAILY_LIMIT = 10;
    private static final long MAX_CACHE_SIZE = 4096;

    private final Clock clock;
    private final Cache<String, State> states = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterAccess(Duration.ofDays(1))
            .build();

    public CommentRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision checkAndRecord(String source) {
        Instant now = clock.instant();
        String dayKey = source + ":" + LocalDate.ofInstant(now, ZoneOffset.UTC);
        var result = new Decision[] {Decision.permitted()};
        states.asMap().compute(dayKey, (key, state) -> {
            List<Instant> recent = state == null ? new ArrayList<>() : new ArrayList<>(state.timestamps());
            recent.removeIf(time -> !time.plus(Duration.ofDays(1)).isAfter(now));
            long shortCount = recent.stream().filter(time -> time.plus(SHORT_WINDOW).isAfter(now)).count();
            if (shortCount >= SHORT_LIMIT) {
                Instant earliest = recent.stream()
                        .filter(time -> time.plus(SHORT_WINDOW).isAfter(now))
                        .min(Instant::compareTo).orElse(now);
                result[0] = new Decision(false, retryAfter(now, earliest.plus(SHORT_WINDOW)));
                return new State(List.copyOf(recent));
            }
            if (recent.size() >= DAILY_LIMIT) {
                result[0] = new Decision(false, retryAfter(now,
                        LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
                return new State(List.copyOf(recent));
            }
            recent.add(now);
            return new State(List.copyOf(recent));
        });
        return result[0];
    }

    long cacheSize() {
        states.cleanUp();
        return states.estimatedSize();
    }

    private static long retryAfter(Instant now, Instant availableAt) {
        return Math.max(1, (Duration.between(now, availableAt).toMillis() + 999) / 1000);
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static Decision permitted() { return new Decision(true, 0); }
    }

    private record State(List<Instant> timestamps) {}
}
