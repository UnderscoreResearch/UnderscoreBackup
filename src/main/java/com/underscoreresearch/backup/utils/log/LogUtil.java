package com.underscoreresearch.backup.utils.log;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.underscoreresearch.backup.configuration.CommandLineModule.DEBUG;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FORCE;
import static com.underscoreresearch.backup.configuration.CommandLineModule.FULL_PATH;
import static com.underscoreresearch.backup.configuration.CommandLineModule.HUMAN_READABLE;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;

/**
 * Utility class providing various logging and formatting functions for the backup system.
 * Includes methods for formatting file sizes, durations, timestamps, and generating status reports.
 * Also provides utilities for debugging and stack trace analysis.
 */
@Slf4j
public final class LogUtil {
    private static final TimeZone LOCAL_TIMEZONE;
    private static final DateTimeFormatter FILE_TIME_FORMATTER;
    private static Boolean logContentErrorsAsErrors;

    static {
        Calendar now = Calendar.getInstance();

        //get current TimeZone using getTimeZone method of Calendar class
        LOCAL_TIMEZONE = now.getTimeZone();

        FILE_TIME_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    }

    /**
     * Checks if debug mode is enabled.
     *
     * @return True if debug mode is enabled, false otherwise
     */
    public static boolean isDebug() {
        InstanceFactory factory = InstanceFactory.getFactory(CommandLine.class);
        return factory == null || InstanceFactory.getInstance(CommandLine.class).hasOption(DEBUG);
    }

    /**
     * Executes a logging operation only if debug mode is enabled.
     *
     * @param log The logging operation to execute
     */
    public static void debug(Runnable log) {
        if (isDebug()) {
            log.run();
        }
    }

    /**
     * Formats a byte size into a human-readable string with appropriate units.
     *
     * @param length The size in bytes
     * @return A formatted string representation of the size
     */
    public static String readableSize(long length) {
        if (length >= 1024 * 1024 * 1024) {
            return String.format("%s GB", formatNumber(((double) length) / 1024 / 1024 / 1024));
        }
        if (length >= 1024 * 1024) {
            return String.format("%s MB", formatNumber(((double) length) / 1024 / 1024));
        }
        if (length >= 1024) {
            return String.format("%s KB", formatNumber(((double) length) / 1024));
        }
        return String.format("%s B", formatNumber(length));
    }

    /**
     * Formats a number with one decimal place.
     *
     * @param num The number to format
     * @return A formatted string representation of the number
     */
    private static String formatNumber(double num) {
        return NumberFormat.getNumberInstance().format(Math.round(num * 10) / 10.0);
    }

    /**
     * Formats a number with thousands separators.
     *
     * @param num The number to format
     * @return A formatted string representation of the number
     */
    public static String readableNumber(long num) {
        return NumberFormat.getNumberInstance().format(num);
    }

    /**
     * Adds a status line for the last processed file to a status list.
     *
     * @param clz The class reporting the status
     * @param ret The list to add the status line to
     * @param lastProcessed The last processed file
     * @param code The status code
     */
    public static void lastProcessedPath(Class<?> clz, List<StatusLine> ret, BackupFile lastProcessed, String code) {
        if (lastProcessed != null) {
            lastProcessedPath(clz, ret, lastProcessed.getPath(), code);
        }
    }

    /**
     * Adds a status line for the last processed path to a status list.
     *
     * @param clz The class reporting the status
     * @param ret The list to add the status line to
     * @param lastProcessed The last processed path
     * @param code The status code
     */
    public static void lastProcessedPath(Class<?> clz, List<StatusLine> ret, String lastProcessed, String code) {
        if (lastProcessed != null) {
            ret.add(new StatusLine(clz, code, "Last processed path",
                    null, PathNormalizer.physicalPath(lastProcessed)));
        }
    }

    /**
     * Formats a duration into a human-readable string.
     *
     * @param duration The duration to format
     * @return A formatted string representation of the duration
     */
    public static String readableDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (duration.toDays() > 0) {
            return String.format("%d days %d:%02d:%02d", duration.toDays(),
                    (seconds / 3600) % 24,
                    (seconds / 60) % 60,
                    seconds % 60);
        }
        if (seconds > 3600) {
            return String.format("%d:%02d:%02d",
                    (seconds / 3600) % 24,
                    (seconds / 60) % 60,
                    seconds % 60);
        }
        return String.format("%d:%02d",
                (seconds / 60) % 60,
                seconds % 60);
    }

    /**
     * Calculates and formats an estimated time of arrival (ETA) based on progress.
     *
     * @param completed The amount of work completed
     * @param total The total amount of work
     * @param elapsedMilliseconds The time elapsed so far
     * @return A formatted string representing the ETA
     */
    public static String readableEta(long completed, long total, Duration elapsedMilliseconds) {
        if (completed > 0) {
            return ", ETA " + readableDuration(Duration.ofMillis(elapsedMilliseconds.toMillis() * total / completed
                    - elapsedMilliseconds.toMillis()));
        }
        return "";
    }

    /**
     * Formats a timestamp into a human-readable date and time string.
     *
     * @param timestamp The timestamp in milliseconds since epoch
     * @return A formatted string representation of the timestamp
     */
    public static String formatTimestamp(Long timestamp) {
        if (timestamp != null) {

            return FILE_TIME_FORMATTER.format(
                    Instant.ofEpochMilli(timestamp).atZone(LOCAL_TIMEZONE.toZoneId()));
        } else {
            return "-";
        }
    }

    /**
     * Generates status lines for throughput reporting.
     *
     * @param clz The class reporting the status
     * @param description A description of the operation
     * @param object The type of object being processed
     * @param totalCount The total number of objects processed
     * @param totalSize The total size of objects processed
     * @param duration The duration of the operation
     * @return A list of status lines
     */
    public static List<StatusLine> getThroughputStatus(Class<?> clz, String description, String object,
                                                       long totalCount, long totalSize, Duration duration) {
        List<StatusLine> ret = new ArrayList<>();

        if (totalCount > 0) {
            String code = description.toUpperCase();
            ret.add(new StatusLine(clz, code + "_OBJECTS", description + " " + object, totalCount));
            ret.add(new StatusLine(clz, code + "_SIZE", description + " total size", totalSize,
                    readableSize(totalSize)));

            if (!duration.isZero()) {
                long elapsedMilliseconds = duration.toMillis();
                if (elapsedMilliseconds > 0) {
                    long throughput = 1000 * totalSize / elapsedMilliseconds;
                    ret.add(new StatusLine(clz, code + "_THROUGHPUT", description + " throughput",
                            throughput, readableSize(throughput) + "/s"));
                }
            }
        }
        return ret;
    }

    /**
     * Formats a backup file for display, with options for human-readable sizes and path formatting.
     *
     * @param commandLine The command line options
     * @param alwaysFull Whether to always show the full path
     * @param file The backup file to format
     * @return A formatted string representation of the file
     */
    public static String printFile(CommandLine commandLine, boolean alwaysFull, ExternalBackupFile file) {
        String size;
        if (file.getLength() != null) {
            if (commandLine.hasOption(HUMAN_READABLE)) {
                size = readableSize(file.getLength());
            } else {
                size = String.valueOf(file.getLength());
            }
        } else {
            size = "-";
        }

        String age = formatTimestamp(file.getLastChanged());

        String strippedPath;
        if (commandLine.hasOption(FULL_PATH) || alwaysFull)
            strippedPath = file.getPath();
        else
            strippedPath = stripPath(file.getPath());

        return String.format("%-10s %-20s %s", size, age, PathNormalizer.physicalPath(strippedPath));
    }

    /**
     * Dumps stack traces for all threads into a string builder, grouped by similar stack traces.
     *
     * @param sb The string builder to append the stack traces to
     */
    public static void dumpAllStackTrace(StringBuilder sb) {
        HashMap<String, NavigableSet<String>> bundles = new HashMap<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread != Thread.currentThread()) {
                StackTraceElement[] elements = trimStackTrace(thread.getStackTrace());
                if (elements != null) {
                    StringBuilder builder = new StringBuilder();
                    String lastElement = null;
                    for (StackTraceElement stackTraceElement : elements) {
                        String element = stackTraceElement.toString();
                        if (!element.equals(lastElement)) {
                            builder.append("\n    ");
                            builder.append(stackTraceElement);
                            lastElement = element;
                        }
                    }
                    String stackString = builder.toString();
                    bundles.computeIfAbsent(stackString,
                            (key) -> new TreeSet<>()).add(thread.getName().replaceAll("\\d+$", "n"));
                }
            }
        }

        NavigableMap<String, String> threadGroups = new TreeMap<>();
        for (Map.Entry<String, NavigableSet<String>> entry : bundles.entrySet()) {
            threadGroups.put(String.join(", ", entry.getValue()), entry.getKey());
        }
        for (Map.Entry<String, String> entry : threadGroups.entrySet()) {
            sb.append("\nThreads: ").append(entry.getKey());
            sb.append(entry.getValue());
        }
    }

    /**
     * Trims a stack trace to include only elements from the application's packages.
     *
     * @param stackTrace The stack trace to trim
     * @return The trimmed stack trace, or null if no relevant elements were found
     */
    private static StackTraceElement[] trimStackTrace(StackTraceElement[] stackTrace) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < stackTrace.length; i++) {
            if (stackTrace[i].getClassName().startsWith("com.underscoreresearch.")) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (last >= 0) {
            return Arrays.copyOfRange(stackTrace, first, last + 1);
        }
        return null;
    }

    /**
     * Logs a content verification message at the appropriate level based on configuration.
     *
     * @param message The message to log
     */
    public static void contentVerificationLogMessage(String message) {
        if (logContentErrorsAsErrors == null) {
            logContentErrorsAsErrors = InstanceFactory.getInstance(CommandLine.class).hasOption(FORCE);
        }
        if (logContentErrorsAsErrors) {
            log.error(message);
        } else {
            log.warn(message);
        }
    }
}
