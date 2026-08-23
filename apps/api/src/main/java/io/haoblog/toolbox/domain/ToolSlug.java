package io.haoblog.toolbox.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ToolSlug {
    private static final Pattern VALID = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private ToolSlug() {}

    public static String normalizeRequired(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Slug is required");
        String value = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "-")
                .replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (value.isEmpty() || value.length() > 160 || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Slug must be lowercase ASCII kebab-case");
        }
        return value;
    }
}
