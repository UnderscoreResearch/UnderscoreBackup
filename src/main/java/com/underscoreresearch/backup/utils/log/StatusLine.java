package com.underscoreresearch.backup.utils.log;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import static com.underscoreresearch.backup.utils.log.LogUtil.readableNumber;

/**
 * Represents a single line of status information from a component of the backup system.
 * Status lines include a reporter, a code, a message, and optional value information.
 * They are used for monitoring and reporting the state of various components.
 */
@Data
public class StatusLine {
    @JsonIgnore
    private Class<?> reporterClass;
    @JsonIgnore
    private String reporter;
    private String code;
    private String message;
    private Long value;
    private Long totalValue;
    private String valueString;

    /**
     * Creates a new StatusLine with the specified reporter class, code, and message.
     *
     * @param reporter The class reporting the status
     * @param code The status code
     * @param message The status message
     */
    public StatusLine(Class<?> reporter, String code, String message) {
        this.reporterClass = reporter;
        this.code = code;
        this.message = message;
    }

    /**
     * Creates a new StatusLine with the specified reporter name, code, and message.
     *
     * @param reporter The name of the reporter
     * @param code The status code
     * @param message The status message
     */
    public StatusLine(String reporter, String code, String message) {
        this.reporter = reporter;
        this.code = code;
        this.message = message;
    }

    /**
     * Creates a new StatusLine with the specified reporter class, code, message, and value.
     *
     * @param reporter The class reporting the status
     * @param code The status code
     * @param message The status message
     * @param value The numeric value
     */
    public StatusLine(Class<?> reporter, String code, String message, Long value) {
        this.reporterClass = reporter;
        this.code = code;
        this.message = message;
        this.value = value;
    }

    /**
     * Creates a new StatusLine with the specified reporter class, code, message, value, and value string.
     *
     * @param reporterClass The class reporting the status
     * @param code The status code
     * @param message The status message
     * @param value The numeric value
     * @param valueString The string representation of the value
     */
    public StatusLine(Class<?> reporterClass, String code, String message, Long value, String valueString) {
        this.reporterClass = reporterClass;
        this.code = code;
        this.message = message;
        this.value = value;
        this.valueString = valueString;
    }

    /**
     * Creates a new StatusLine with the specified reporter class, code, message, value, total value, and value string.
     *
     * @param reporterClass The class reporting the status
     * @param code The status code
     * @param message The status message
     * @param value The numeric value
     * @param totalValue The total value (for progress reporting)
     * @param valueString The string representation of the value
     */
    public StatusLine(Class<?> reporterClass, String code, String message, Long value, Long totalValue,
                      String valueString) {
        this.reporterClass = reporterClass;
        this.code = code;
        this.message = message;
        this.value = value;
        this.totalValue = totalValue;
        this.valueString = valueString;
    }

    /**
     * Gets the simplified name of the reporter.
     * If a reporter string is provided, returns the last component of the name.
     * Otherwise, returns the simple name of the reporter class.
     *
     * @return The simplified reporter name
     */
    public String getReporter() {
        if (reporter != null) {
            int lastIndex = reporter.lastIndexOf('.');
            if (lastIndex >= 0)
                return reporter.substring(lastIndex + 1);
            return reporter;
        }
        return reporterClass.getSimpleName();
    }

    /**
     * Gets the string representation of the value.
     * If no value string is explicitly set but a numeric value is available,
     * formats the numeric value as a readable number.
     *
     * @return The string representation of the value
     */
    public String getValueString() {
        if (valueString == null && value != null)
            return readableNumber(value);
        return valueString;
    }

    /**
     * Returns a string representation of this status line,
     * combining the message and value string if available.
     *
     * @return A string representation of the status line
     */
    @Override
    public String toString() {
        String value = getValueString();
        return message + (value != null ? ": " + getValueString() : "");
    }
}
