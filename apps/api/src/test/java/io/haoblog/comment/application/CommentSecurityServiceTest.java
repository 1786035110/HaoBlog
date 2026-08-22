package io.haoblog.comment.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentSecurityServiceTest {
    private static final UUID COMMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CommentSecurityService security = new CommentSecurityService(
            Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));

    @Test
    void encryptsEmailWithAuthenticatedRandomNonce() {
        var first = security.encryptEmail(COMMENT_ID, "author@example.test");
        var second = security.encryptEmail(COMMENT_ID, "author@example.test");

        assertNotEquals(Base64.getEncoder().encodeToString(first.nonce()), Base64.getEncoder().encodeToString(second.nonce()));
        assertEquals("author@example.test", security.decryptEmail(COMMENT_ID, first));
        assertThrows(IllegalArgumentException.class, () -> security.decryptEmail(UUID.randomUUID(), first));
    }

    @Test
    void derivesScopedDigests() {
        var today = security.dailyIpHmac("192.0.2.1", LocalDate.of(2026, 8, 22));
        var tomorrow = security.dailyIpHmac("192.0.2.1", LocalDate.of(2026, 8, 23));

        assertFalse(java.util.Arrays.equals(today, tomorrow));
        assertArrayEquals(security.deleteTokenDigest("token"), security.deleteTokenDigest("token"));
        assertTrue(security.contentFingerprint(COMMENT_ID, "body").length == 32);
    }

    @Test
    void rejectsInvalidKey() {
        assertThrows(IllegalArgumentException.class, () -> new CommentSecurityService(
                Base64.getEncoder().encodeToString(new byte[16])));
    }
}
