package io.haoblog.content.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Slug {
    private static final Pattern VALID = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private Slug() {}

    public static String normalizeNullable(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isEmpty() || normalized.length() > 160 || !VALID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Slug must be lowercase ASCII kebab-case");
        }
        return normalized;
    }

    public static String normalizeRequired(String raw) {
        String normalized = normalizeNullable(raw);
        if (normalized == null) throw new IllegalArgumentException("Slug is required");
        return normalized;
    }
}
