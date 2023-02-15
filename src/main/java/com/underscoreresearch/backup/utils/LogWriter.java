package com.underscoreresearch.backup.utils;

import static com.underscoreresearch.backup.cli.web.ConfigurationPost.setReadOnlyFilePermissions;
import static com.underscoreresearch.backup.configuration.CommandLineModule.LOG_FILE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

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

import com.underscoreresearch.backup.configuration.InstanceFactory;

@Plugin(name = "LogWriter",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE)
@Slf4j
public class LogWriter extends AbstractAppender {
    private static final long MAXIMUM_FILE_AGE = Duration.ofDays(7).toMillis();
    private static Map<String, LogWriter> APPENDERS = new HashMap<>();
    private FileOutputStream stream;
    private boolean initialized;
    long creationDate;

    protected LogWriter(String name, Filter filter, Layout<String> layout) {
        super(name, filter, layout, true, Property.EMPTY_ARRAY);
    }

    @PluginFactory
    public static synchronized LogWriter createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<String> layout) {
        return new LogWriter(name, filter, layout);
    }

    private synchronized void setup() {
        if (initialized) {
            if (creationDate + MAXIMUM_FILE_AGE < System.currentTimeMillis()) {
                initialized = false;
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                rotateLogs();
            } else {
                return;
            }
        }

        if (!initialized) {
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
                        file.getParentFile().mkdirs();
                        creationDate = System.currentTimeMillis();
                    } else {
                        try {
                            FileTime creationTime = (FileTime) Files.getAttribute(file.toPath(), "creationTime");
                            creationDate = creationTime.toMillis();
                        } catch (IOException ex) {
                            log.error("Failed to read log timestamp", ex);
                            creationDate = System.currentTimeMillis();
                        }
                    }

                    stream = new FileOutputStream(fileName, true);

                    if (!exists) {
                        setReadOnlyFilePermissions(new File(fileName));
                    }
                }
            } catch (IOException e) {
                System.err.println(String.format("Can't open %s for writing, disabling file logging", fileName));
                e.printStackTrace(System.err);
            }
        }
    }

    private void rotateLogs() {
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
                new File(nextFileName).delete();
                if (!file.renameTo(new File(nextFileName))) {
                    log.error("Failed to rename log file {} to {}", fileName, nextFileName);
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
            log.error("Failed to write to compress log", e);
        }

        File file = new File(baseFileName);
        file.delete();
    }

    @Override
    public void append(LogEvent event) {
        setup();

        if (stream != null) {
            try {
                stream.write(getLayout().toByteArray(event));
                stream.flush();
            } catch (IOException e) {
                System.err.println("Failed to write to log");
                e.printStackTrace(System.err);
            }
        }
    }
}