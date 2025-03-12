package com.underscoreresearch.backup.utils;

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

    public static boolean isDebug() {
        InstanceFactory factory = InstanceFactory.getFactory(CommandLine.class);
        return factory == null || InstanceFactory.getInstance(CommandLine.class).hasOption(DEBUG);
    }

    public static void debug(Runnable log) {
        if (isDebug()) {
            log.run();
        }
    }

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

    private static String formatNumber(double num) {
        return NumberFormat.getNumberInstance().format(Math.round(num * 10) / 10.0);
    }

    public static String readableNumber(long num) {
        return NumberFormat.getNumberInstance().format(num);
    }

    public static void lastProcessedPath(Class<?> clz, List<StatusLine> ret, BackupFile lastProcessed, String code) {
        if (lastProcessed != null) {
            lastProcessedPath(clz, ret, lastProcessed.getPath(), code);
        }
    }

    public static void lastProcessedPath(Class<?> clz, List<StatusLine> ret, String lastProcessed, String code) {
        if (lastProcessed != null) {
            ret.add(new StatusLine(clz, code, "Last processed path",
                    null, PathNormalizer.physicalPath(lastProcessed)));
        }
    }

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

    public static String readableEta(long completed, long total, Duration elapsedMilliseconds) {
        if (completed > 0) {
            return ", ETA " + readableDuration(Duration.ofMillis(elapsedMilliseconds.toMillis() * total / completed
                    - elapsedMilliseconds.toMillis()));
        }
        return "";
    }

    public static String formatTimestamp(Long timestamp) {
        if (timestamp != null) {

            return FILE_TIME_FORMATTER.format(
                    Instant.ofEpochMilli(timestamp).atZone(LOCAL_TIMEZONE.toZoneId()));
        } else {
            return "-";
        }
    }

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
