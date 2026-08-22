package io.haoblog.content.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlugTest {
    @Test
    void normalizesAsciiKebabInput() {
        assertEquals("night-signal-baseline", Slug.normalizeNullable("  NIGHT__Signal   Baseline  "));
        assertEquals("a-b", Slug.normalizeNullable("--a---b--"));
    }

    @Test
    void allowsEmptyDraftSlugButRequiresPublishedSlug() {
        assertNull(Slug.normalizeNullable(null));
        assertNull(Slug.normalizeNullable("   "));
        assertThrows(IllegalArgumentException.class, () -> Slug.normalizeRequired(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new Article(null, "Published", null, "# published", ArticleStatus.PUBLISHED, java.time.Instant.now(), java.time.Instant.now()));
    }

    @Test
    void rejectsNonAsciiAndInvalidBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> Slug.normalizeNullable("中文文章"));
        assertThrows(IllegalArgumentException.class, () -> Slug.normalizeNullable("a/b"));
        assertThrows(IllegalArgumentException.class, () -> Slug.normalizeNullable("a".repeat(161)));
    }
}
