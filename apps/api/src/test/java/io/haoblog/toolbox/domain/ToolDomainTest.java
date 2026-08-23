package io.haoblog.toolbox.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolDomainTest {
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Test
    void acceptsOnlyWhitelistedEmbeddedComponent() {
        Tool tool = new Tool(CATEGORY_ID, ToolType.EMBEDDED, ToolStatus.ACTIVE, "JSON", "json",
                null, null, null, ToolComponentKey.JSON_FORMAT, List.of("json"), 0, Instant.now());
        assertEquals("json-format", tool.getComponentKey().getValue());
        assertThrows(IllegalArgumentException.class, () -> ToolComponentKey.fromValue("/components/Arbitrary.vue"));
    }

    @Test
    void rejectsUnsafeUrlsAndMismatchedFields() {
        assertThrows(IllegalArgumentException.class, () -> new Tool(CATEGORY_ID, ToolType.LINK, ToolStatus.ACTIVE,
                "HTTP", "http", null, "http://example.com", null, null, List.of(), 0, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new Tool(CATEGORY_ID, ToolType.EMBEDDED, ToolStatus.ACTIVE,
                "Mixed", "mixed", null, "https://example.com", null, ToolComponentKey.JSON_FORMAT, List.of(), 0, Instant.now()));
    }
}
