package io.haoblog.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {
    private static final SecureRandom RANDOM = new SecureRandom();
    private UuidV7() {}

    public static UUID generate() {
        long timestamp = System.currentTimeMillis() & 0x0000FFFFFFFFFFFFL;
        long randomA = RANDOM.nextInt(1 << 12);
        long randomB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long most = (timestamp << 16) | (0x7L << 12) | randomA;
        long least = 0x8000000000000000L | randomB;
        return new UUID(most, least);
    }
}
