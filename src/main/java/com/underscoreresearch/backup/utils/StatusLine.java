package com.underscoreresearch.backup.utils;

import static com.underscoreresearch.backup.utils.LogUtil.readableNumber;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class StatusLine {
    @JsonIgnore
    private Class reporterClass;
    @JsonIgnore
    private String reporter;
    private String code;
    private String message;
    private Long value;
    private String valueString;

    public String getReporter() {
        if (reporter != null) {
            int lastIndex = reporter.lastIndexOf('.');
            if (lastIndex >= 0)
                return reporter.substring(lastIndex + 1);
            return reporter;
        }
        return reporterClass.getSimpleName();
    }

    public void setReporter(String name) {
        throw new UnsupportedOperationException();
    }

    public StatusLine(Class reporter, String code, String message) {
        this.reporterClass = reporter;
        this.code = code;
        this.message = message;
    }

    public StatusLine(String reporter, String code, String message) {
        this.reporter = reporter;
        this.code = code;
        this.message = message;
    }

    public StatusLine(Class reporter, String code, String message, Long value) {
        this.reporterClass = reporter;
        this.code = code;
        this.message = message;
        this.value = value;
    }

    public StatusLine(Class reporterClass, String code, String message, Long value, String valueString) {
        this.reporterClass = reporterClass;
        this.code = code;
        this.message = message;
        this.value = value;
        this.valueString = valueString;
    }

    public String getValueString() {
        if (valueString == null && value != null)
            return readableNumber(value);
        return valueString;
    }

    @Override
    public String toString() {
        return message + ": " + getValueString();
    }
}
