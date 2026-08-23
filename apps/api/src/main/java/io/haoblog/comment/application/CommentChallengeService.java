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
        return validateAndConsume(articleId, token) == Validation.VALID;
    }

    public Validation validateAndConsume(UUID articleId, String token) {
        if (token == null || token.isBlank()) return Validation.INVALID;
        Instant now = clock.instant();
        var result = new Validation[] {Validation.INVALID};
        challenges.asMap().computeIfPresent(token, (key, challenge) -> {
            if (!challenge.articleId().equals(articleId)) return challenge;
            if (now.isBefore(challenge.issuedAt().plus(MINIMUM_FILL_TIME))) {
                result[0] = Validation.TOO_EARLY;
                return challenge;
            }
            if (!challenge.expiresAt().isAfter(now)) {
                result[0] = Validation.EXPIRED;
                return null;
            }
            result[0] = Validation.VALID;
            return null;
        });
        return result[0];
    }

    long cacheSize() {
        challenges.cleanUp();
        return challenges.estimatedSize();
    }

    public record IssuedChallenge(String token, Instant expiresAt) {}

    public enum Validation { VALID, INVALID, TOO_EARLY, EXPIRED }

    private record Challenge(UUID articleId, Instant issuedAt, Instant expiresAt) {}
}
