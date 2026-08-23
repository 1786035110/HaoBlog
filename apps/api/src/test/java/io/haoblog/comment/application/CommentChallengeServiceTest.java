package io.haoblog.comment.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentChallengeServiceTest {
    @Test
    void requiresMinimumAgeAndConsumesOnce() {
        var clock = new MutableClock(Instant.parse("2026-08-22T00:00:00Z"));
        var security = new CommentSecurityService(Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        var challenges = new CommentChallengeService(clock, security);
        UUID articleId = UUID.randomUUID();
        var issued = challenges.issue(articleId);

        assertFalse(challenges.consume(articleId, issued.token()));

        clock.advance(Duration.ofSeconds(3));
        assertTrue(challenges.consume(articleId, issued.token()));
        assertFalse(challenges.consume(articleId, issued.token()));
    }

    @Test
    void rejectsExpiredChallenge() {
        var clock = new MutableClock(Instant.parse("2026-08-22T00:00:00Z"));
        var security = new CommentSecurityService(Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
        var challenges = new CommentChallengeService(clock, security);
        UUID articleId = UUID.randomUUID();
        var issued = challenges.issue(articleId);

        clock.advance(Duration.ofHours(2).plusSeconds(1));
        assertFalse(challenges.consume(articleId, issued.token()));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
