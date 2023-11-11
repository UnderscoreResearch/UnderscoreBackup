package com.underscoreresearch.backup.cli.ui;

import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.getConfigurationUrl;
import static com.underscoreresearch.backup.cli.commands.ConfigureCommand.validateConfigurationUrl;
import static com.underscoreresearch.backup.configuration.CommandLineModule.NOTIFICATION_LOCATION;
import static com.underscoreresearch.backup.io.IOUtils.deleteFile;
import static com.underscoreresearch.backup.utils.LogUtil.debug;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.commands.ConfigureCommand;
import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
public class AwtFileUIManager extends AwtUIManager {
    private static final long MAXIMUM_AGE_MS = 10 * 1000;
    private final WatchService watchService;

    public AwtFileUIManager() {
        try {
            watchService = FileSystems.getDefault().newWatchService();

            Path path = FileSystems.getDefault().getPath(InstanceFactory.getInstance(NOTIFICATION_LOCATION));
            path.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
            Thread thread = new Thread(this::pollingThread, "NotificationWatcher");
            thread.setDaemon(true);
            thread.start();

            File[] files = path.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    processFile(file);
                }
            }
        } catch (IOException e) {
            displayErrorMessage("Failed to create watch service");
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void displayStartedMessage() {
    }

    private void pollingThread() {
        while (true) {
            try {
                WatchKey key = watchService.take();
                if (key != null) {
                    Path watchedPath = (Path) key.watchable();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        File file = watchedPath.resolve((Path) event.context()).toFile();
                        processFile(file);
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private synchronized void processFile(File file) {
        try {
            if (file.exists() && System.currentTimeMillis() - Files.getLastModifiedTime(file.toPath()).toMillis() < MAXIMUM_AGE_MS) {
                try {
                    String message;
                    try (FileReader reader = new FileReader(file)) {
                        StringWriter writer = new StringWriter();
                        reader.transferTo(writer);
                        message = writer.toString();
                    }
                    deleteFile(file);

                    switch (file.getName()) {
                        case "error" -> displayErrorMessage(message);
                        case "info" -> displayInfoMessage(message);
                        case "open" -> {
                            if (message.contains("://")) {
                                openUri(URI.create(message));
                            } else {
                                openFolder(new File(message));
                            }
                        }
                        case "tooltip" -> setTooltip(message);
                        default -> log.warn("Unknown notification file: {}", file);
                    }

                } catch (FileNotFoundException e) {
                    debug(() -> log.debug("Notification file {} disappeared before read", file));
                } catch (IOException e) {
                    log.warn("Failed to read notification file {}", file, e);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to get last modified time for \"{}\"", file.getAbsolutePath(), e);
        }
    }

    @Override
    protected void launchConfig() {
        try {
            String url = getConfigurationUrl();
            validateConfigurationUrl(url);
            openUri(URI.create(url));
        } catch (ConfigureCommand.ConfigurationUrlException exc) {
            displayErrorMessage(exc.getMessage());
        } catch (IOException e) {
            log.warn("Failed to open configuration url", e);
        }
    }
}
