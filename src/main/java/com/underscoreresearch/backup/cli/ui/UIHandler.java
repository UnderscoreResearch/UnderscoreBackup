package com.underscoreresearch.backup.cli.ui;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class UIHandler {

    private static final Duration MINIMUM_WAIT_DURATION = Duration.ofSeconds(20);
    private static final List<CloseableTask> activeTasks = new ArrayList<>();
    private static Instant lastMessage;
    private static UIManager uiManager;

    public static void setup(UIManager manager) {
        uiManager = manager;
    }

    public static void setup() {
        if (SystemUtils.IS_OS_MAC_OSX) {
            uiManager = new OsxUIManager();
        } else {
            uiManager = new AwtUIManager();
        }
    }

    public static synchronized void displayErrorMessage(String message) {
        if (uiManager != null && rateLimit()) {
            uiManager.displayErrorMessage(message);
        }
    }

    private static synchronized boolean rateLimit() {
        if (lastMessage == null || lastMessage.plus(MINIMUM_WAIT_DURATION).isBefore(Instant.now())) {
            lastMessage = Instant.now();
            return true;
        }
        return false;
    }

    public static synchronized void displayInfoMessage(String message) {
        if (uiManager != null && rateLimit()) {
            if (InstanceFactory.hasConfiguration(true)) {
                BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);
                if (config.getManifest() != null && config.getManifest().getHideNotifications() != null
                        && config.getManifest().getHideNotifications()) {
                    return;
                }
            }
            uiManager.displayInfoMessage(message);
        }
    }

    public static void openFolder(File path) {
        if (uiManager != null)
            uiManager.openFolder(path);
    }

    public static Closeable registerTask(String message) {
        var task = new CloseableTask(message);
        synchronized (activeTasks) {
            activeTasks.add(task);
        }
        updateTooltip();
        return task;
    }

    public static boolean isActive() {
        synchronized (activeTasks) {
            return !activeTasks.isEmpty();
        }
    }

    private static void updateTooltip() {
        String message;
        synchronized (activeTasks) {
            if (activeTasks.isEmpty()) {
                message = "Underscore Backup - Idle";
            } else {
                message = "Underscore Backup - " + activeTasks.get(activeTasks.size() - 1).getMessage();
            }
        }
        if (uiManager != null) {
            uiManager.setTooltip(message);
        }
    }

    private static void removeTask(CloseableTask task) {
        synchronized (activeTasks) {
            activeTasks.remove(task);
        }
        updateTooltip();
    }

    public static void openUri(URI uri) {
        if (uiManager != null) {
            uiManager.openUri(uri);
        }
    }

    @Getter
    @AllArgsConstructor
    private static class CloseableTask implements Closeable {
        private String message;

        @Override
        public void close() throws IOException {
            removeTask(this);
        }
    }
}
