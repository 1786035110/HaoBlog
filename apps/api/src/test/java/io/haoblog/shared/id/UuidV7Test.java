package io.haoblog.shared.id;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class UuidV7Test {
    @Test void generatesVersionSevenRfcUuid() {
        UUID id = UuidV7.generate();
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
        long epochMillis = id.getMostSignificantBits() >>> 16;
        assertTrue(Instant.ofEpochMilli(epochMillis).isAfter(Instant.now().minusSeconds(5)));
        assertTrue(Instant.ofEpochMilli(epochMillis).isBefore(Instant.now().plusSeconds(5)));
    }
}
