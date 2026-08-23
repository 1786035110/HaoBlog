package io.haoblog.toolbox.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ToolComponentKeyConverter implements AttributeConverter<ToolComponentKey, String> {
    @Override
    public String convertToDatabaseColumn(ToolComponentKey attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ToolComponentKey convertToEntityAttribute(String value) {
        return ToolComponentKey.fromValue(value);
    }
}
