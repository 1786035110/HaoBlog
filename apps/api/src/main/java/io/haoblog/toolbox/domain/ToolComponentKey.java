package io.haoblog.toolbox.domain;

public enum ToolComponentKey {
    JSON_FORMAT("json-format"), BASE64("base64"), URL_CODEC("url-codec"), TIMESTAMP("timestamp"), REGEX_TEST("regex-test");

    private final String value;

    ToolComponentKey(String value) { this.value = value; }

    public String getValue() { return value; }

    public static ToolComponentKey fromValue(String value) {
        if (value == null || value.isBlank()) return null;
        for (ToolComponentKey key : values()) if (key.value.equals(value)) return key;
        throw new IllegalArgumentException("componentKey is not allowed");
    }
}
