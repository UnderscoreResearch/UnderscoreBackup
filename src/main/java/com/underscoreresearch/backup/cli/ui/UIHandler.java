package com.underscoreresearch.backup.cli.ui;

import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.SystemUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class UIHandler {

    private static final Duration MINIMUM_WAIT_DURATION = Duration.ofSeconds(20);
    private static final List<CloseableTask> activeTasks = new ArrayList<>();
    private static final Duration MINIMUM_ACTIVE = Duration.ofSeconds(30);
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

    public static Closeable registerTask(String message, boolean active) {
        var task = new CloseableTask(message, active);
        synchronized (activeTasks) {
            activeTasks.add(task);
        }
        updateTooltip();
        return task;
    }

    public static boolean isLongActive() {
        synchronized (activeTasks) {
            if (InstanceFactory.isShutdown())
                return true;
            if (!activeTasks.isEmpty()) {
                CloseableTask task = activeTasks.getLast();
                if (Instant.now().minus(MINIMUM_ACTIVE).isAfter(task.getStarted())) {
                    return task.isActive();
                }
            }
            return false;
        }
    }

    public static String getActiveTaskMessage() {
        synchronized (activeTasks) {
            if (!activeTasks.isEmpty()) {
                CloseableTask lastTask = activeTasks.getLast();
                if (lastTask.isActive())
                    return lastTask.getMessage();
            }
            if (InstanceFactory.isShutdown()) {
                return "Reloading configuration or shutting down";
            }
            return null;
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
    private static class CloseableTask implements Closeable {
        private final String message;
        private final boolean active;
        private final Instant started;

        private CloseableTask(String message, boolean active) {
            this.message = message;
            this.active = active;
            this.started = Instant.now();
        }

        @Override
        public void close() throws IOException {
            removeTask(this);
        }
    }
}
