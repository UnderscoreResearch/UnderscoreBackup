package com.underscoreresearch.backup.utils;

import static com.underscoreresearch.backup.configuration.CommandLineModule.DEBUG;
import static com.underscoreresearch.backup.configuration.CommandLineModule.HUMAN_READABLE;
import static com.underscoreresearch.backup.model.BackupActivePath.stripPath;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.cli.CommandLine;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
public final class LogUtil {
    private static final TimeZone LOCAL_TIMEZONE;
    private static final DateTimeFormatter FILE_TIME_FORMATTER;

    static {
        Calendar now = Calendar.getInstance();

        //get current TimeZone using getTimeZone method of Calendar class
        LOCAL_TIMEZONE = now.getTimeZone();
        ;

        FILE_TIME_FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
    }

    public static void debug(Runnable log) {
        InstanceFactory factory = InstanceFactory.getFactory(CommandLine.class);
        if (factory == null || factory.getInstance(CommandLine.class).hasOption(DEBUG)) {
            log.run();
        }
    }

    public static String readableSize(long length) {
        if (length >= 1024 * 1024 * 1024) {
            return String.format("%.1f GB", ((double) length) / 1024 / 1024 / 1024);
        }
        if (length >= 1024 * 1024) {
            return String.format("%.1f MB", ((double) length) / 1024 / 1024);
        }
        if (length >= 1024) {
            return String.format("%.1f KB", ((double) length) / 1024);
        }
        return String.format("%s B", length);
    }

    public static String readableNumber(long num) {
        return NumberFormat.getNumberInstance().format(num);
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

    public static String formatTimestamp(Long timestamp) {
        if (timestamp != null) {

            return FILE_TIME_FORMATTER.format(
                    Instant.ofEpochMilli(timestamp).atZone(LOCAL_TIMEZONE.toZoneId()));
        } else {
            return "-";
        }
    }

    public static List<StatusLine> getThroughputStatus(Class clz, String description, String object,
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

    public static String printFile(CommandLine commandLine, BackupFile file) {
        String size;
        if (file.getLength() != null) {
            if (commandLine.hasOption(HUMAN_READABLE)) {
                size = readableSize(file.getLength());
            } else {
                size = file.getLength() + "";
            }
        } else {
            size = "-";
        }

        String age = formatTimestamp(file.getLastChanged());

        String strippedPath = stripPath(file.getPath());

        return String.format("%-10s %-20s %s", size, age, strippedPath);
    }

}
