package com.underscoreresearch.backup.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StatusLine {
    private Class reporter;
    private String code;
    private String description;
    private Long value;
    private String valueString;

    public StatusLine(Class reporter, String code, String description, Long value) {
        this.reporter = reporter;
        this.code = code;
        this.description = description;
        this.value = value;
    }

    public String getValueString() {
        if (valueString == null && value != null)
            return value.toString();
        return valueString;
    }

    @Override
    public String toString() {
        return description + ": " + getValueString();
    }
}
