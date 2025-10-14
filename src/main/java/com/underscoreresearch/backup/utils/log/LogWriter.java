package com.underscoreresearch.backup.utils.log;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.file.implementation.BackupStatsLogger;
import com.underscoreresearch.backup.io.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.util.Strings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import static com.underscoreresearch.backup.configuration.CommandLineModule.LOG_FILE;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.io.IOUtils.deleteFileException;

/**
 * A Log4j2 appender that writes log events to a file with automatic rotation.
 * This appender manages log files with the following features:
 * - Automatic log rotation based on age
 * - Compression of rotated logs
 * - Secure file permissions for log files
 * - Handling of initialization errors
 * - Extraction of log creation date from log content
 */
@Plugin(name = "LogWriter",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE)
@Slf4j
public class LogWriter extends AbstractAppender {
    private static final long MAXIMUM_FILE_AGE = Duration.ofDays(7).toMillis();
    private static final Pattern LOG_TIMESTAMP = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
    private final static DateTimeFormatter LOG_FILE_FORMATTER
            = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration NEXT_ROTATION = Duration.ofHours(1);
    long creationDate;
    private FileOutputStream stream;
    private boolean initialized;
    private List<Runnable> unPostedLogs = new ArrayList<>();
    private Instant lastRotationTry = null;

    /**
     * Constructs a new LogWriter with the specified name, filter, and layout.
     *
     * @param name   The name of the appender
     * @param filter The filter to apply to log events
     * @param layout The layout to format log events
     */
    protected LogWriter(String name, Filter filter, Layout<String> layout) {
        super(name, filter, layout, true, Property.EMPTY_ARRAY);
    }

    /**
     * Factory method to create a LogWriter instance.
     *
     * @param name   The name of the appender
     * @param filter The filter to apply to log events
     * @param layout The layout to format log events
     * @return The LogWriter instance
     */
    @PluginFactory
    public static synchronized LogWriter createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<String> layout) {
        return new LogWriter(name, filter, layout);
    }

    /**
     * Sets up the log file stream if it hasn't been initialized yet or needs rotation.
     * This method handles log file creation, permission setting, and rotation.
     */
    private synchronized void setup() {
        if (stream != null) {
            if (logExpired()) {
                initialized = false;
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    stream = null;
                }

                rotateLogs();
            } else {
                return;
            }
        }

        Exception initError = null;

        if (!initialized) {
            if (!InstanceFactory.isInitialized()) {
                unPostedLogs.add(() -> log.error("Logging before properly initialized"));
                return;
            }

            initialized = true;
            String fileName;
            try {
                fileName = InstanceFactory.getInstance(LOG_FILE);
            } catch (Exception exc) {
                // Can't find a log filename. I guess we don't want logs.
                return;
            }
            try {
                if (!Strings.isEmpty(fileName)) {
                    File file = new File(fileName);
                    boolean exists = file.exists();
                    if (!exists) {
                        createDirectory(file.getParentFile(), true);
                        creationDate = System.currentTimeMillis();
                    } else {
                        try {
                            // It seems creation timestamps are not reliable on all systems.
                            // Just going to parse the first timestamp in the log instead.
                            creationDate = 0;
                            try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
                                while (creationDate == 0L) {
                                    String line = reader.readLine();
                                    if (line == null) {
                                        creationDate = System.currentTimeMillis();
                                        break;
                                    }
                                    Matcher match = LOG_TIMESTAMP.matcher(line);
                                    if (match.find()) {
                                        try {
                                            LocalDateTime time = LOG_FILE_FORMATTER.parse(match.group(), LocalDateTime::from);
                                            creationDate = time.toEpochSecond(OffsetDateTime.now().getOffset()) * 1000;
                                        } catch (Exception e) {
                                            initError = e;
                                        }
                                    }
                                }
                            }
                        } catch (IOException ex) {
                            initError = ex;
                            creationDate = System.currentTimeMillis();
                        }
                    }

                    stream = new FileOutputStream(fileName, true);

                    if (!exists) {
                        IOUtils.setOwnerOnlyPermissions(new File(fileName));
                    }
                }
            } catch (IOException e) {
                System.err.printf("Can't open %s for writing, disabling file logging%n", fileName);
                e.printStackTrace(System.err);
            }
        }

        if (initError != null) {
            Exception exc = initError;
            unPostedLogs.add(() -> log.warn("Encountered issue initializing log", exc));
        }
    }

    /**
     * Checks if the current log file has expired and should be rotated.
     * Limits rotation attempts to once per hour to prevent excessive rotation attempts.
     *
     * @return True if the log file has expired and should be rotated, false otherwise
     */
    private boolean logExpired() {
        if (creationDate + MAXIMUM_FILE_AGE < System.currentTimeMillis()) {
            Instant now = Instant.now();
            if (lastRotationTry != null && lastRotationTry.plus(NEXT_ROTATION).isAfter(now)) {
                return false;
            }
            lastRotationTry = now;
            return true;
        }
        return false;
    }

    /**
     * Rotates log files by compressing the current log file and shifting existing rotated logs.
     * Maintains up to 9 rotated log files (1-9).
     */
    private synchronized void rotateLogs() {
        String baseFileName;
        try {
            baseFileName = InstanceFactory.getInstance(LOG_FILE);
        } catch (Exception exc) {
            // Can't find a log filename. I guess we don't want logs.
            return;
        }

        for (int i = 8; i >= 1; i--) {
            String fileName = baseFileName + "." + i + ".gz";
            String nextFileName = baseFileName + "." + (i + 1) + ".gz";

            File file = new File(fileName);
            if (file.exists()) {
                deleteFile(new File(nextFileName));
                if (!file.renameTo(new File(nextFileName))) {
                    unPostedLogs.add(() -> log.error("Failed to rename log file \"{}\" to \"{}\"", fileName, nextFileName));
                    return;
                }
            }
        }

        try (FileOutputStream stream = new FileOutputStream(baseFileName + ".1.gz")) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(stream)) {
                try (FileInputStream inputStream = new FileInputStream(baseFileName)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = inputStream.read(buffer)) > 0) {
                        gzip.write(buffer, 0, read);
                    }
                }
                gzip.write("".getBytes());
            }
        } catch (IOException e) {
            unPostedLogs.add(() -> log.error("Failed to write to compress log", e));
        }

        try {
            IOUtils.setOwnerOnlyPermissions(new File(baseFileName + ".1.gz"));
        } catch (IOException e) {
            unPostedLogs.add(() -> log.error("Failed to set compressed log file read only"));
        }

        deleteFile(new File(baseFileName));
    }

    /**
     * Deletes a file and logs any errors that occur.
     *
     * @param file The file to delete
     */
    private void deleteFile(File file) {
        try {
            deleteFileException(file);
        } catch (IOException e) {
            unPostedLogs.add(() -> log.warn(e.getMessage()));
        }
    }

    /**
     * Processes a log event, writing it to the log file and handling any errors.
     * Error and fatal level events are also written to standard error.
     *
     * @param event The log event to process
     */
    @Override
    public synchronized void append(LogEvent event) {
        setup();

        if (stream != null) {
            try {
                byte[] error = getLayout().toByteArray(event);
                stream.write(error);
                stream.flush();

                switch (event.getLevel().getStandardLevel()) {
                    case FATAL:
                    case ERROR:
                        BackupStatsLogger.writeEncounteredError(error);
                        System.err.write(error);
                        break;
                }
            } catch (IOException e) {
                System.err.println("Failed to write to log");
                e.printStackTrace(System.err);
            }

            if (!unPostedLogs.isEmpty()) {
                List<Runnable> postingLogs = unPostedLogs;
                unPostedLogs = new ArrayList<>();

                Thread thread = new Thread(() -> {
                    for (Runnable runnable : postingLogs) {
                        runnable.run();
                    }
                }, "LogWriterError");
                thread.setDaemon(true);
                thread.start();
            }
        } else {
            if (!unPostedLogs.isEmpty()) {
                unPostedLogs.clear();
                System.err.println("Failed to initialize log file");
            }
        }
    }
}
