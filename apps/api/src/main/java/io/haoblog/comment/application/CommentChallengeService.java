package io.haoblog.comment.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class CommentChallengeService {
    static final Duration MINIMUM_FILL_TIME = Duration.ofSeconds(3);
    static final Duration MAXIMUM_AGE = Duration.ofHours(2);
    private static final long MAX_CACHE_SIZE = 4096;

    private final Clock clock;
    private final CommentSecurityService security;
    private final Cache<String, Challenge> challenges = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(MAXIMUM_AGE)
            .build();

    public CommentChallengeService(Clock clock, CommentSecurityService security) {
        this.clock = clock;
        this.security = security;
    }

    public IssuedChallenge issue(UUID articleId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(MAXIMUM_AGE);
        String token = security.challengeToken(articleId, issuedAt);
        challenges.put(token, new Challenge(articleId, issuedAt, expiresAt));
        return new IssuedChallenge(token, expiresAt);
    }

    public boolean consume(UUID articleId, String token) {
        if (token == null || token.isBlank()) return false;
        Challenge challenge = challenges.getIfPresent(token);
        if (challenge == null) return false;
        Instant now = clock.instant();
        if (!challenge.articleId().equals(articleId)
                || now.isBefore(challenge.issuedAt().plus(MINIMUM_FILL_TIME))
                || !challenge.expiresAt().isAfter(now)) {
            return false;
        }
        challenges.invalidate(token);
        return true;
    }

    long cacheSize() {
        challenges.cleanUp();
        return challenges.estimatedSize();
    }

    public record IssuedChallenge(String token, Instant expiresAt) {}

    private record Challenge(UUID articleId, Instant issuedAt, Instant expiresAt) {}
}
