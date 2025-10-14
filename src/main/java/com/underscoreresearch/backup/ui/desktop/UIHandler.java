package com.underscoreresearch.backup.ui.desktop;

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

/**
 * Central handler for user interface interactions across different platforms.
 * This class manages notifications, task tracking, and UI interactions,
 * delegating to platform-specific implementations.
 */
@Slf4j
public class UIHandler {

    private static final Duration MINIMUM_WAIT_DURATION = Duration.ofSeconds(20);
    private static final List<CloseableTask> activeTasks = new ArrayList<>();
    private static final Duration MINIMUM_ACTIVE = Duration.ofSeconds(30);
    private static Instant lastMessage;
    private static UIManager uiManager;

    /**
     * Sets up the UI handler with a specific UI manager.
     *
     * @param manager The UI manager to use
     */
    public static void setup(UIManager manager) {
        uiManager = manager;
    }

    /**
     * Sets up the UI handler with a platform-appropriate UI manager.
     */
    public static void setup() {
        if (SystemUtils.IS_OS_MAC_OSX) {
            uiManager = new OsxUIManager();
        } else {
            uiManager = new AwtUIManager();
        }
    }

    /**
     * Displays an error message to the user.
     *
     * @param message The error message to display
     */
    public static synchronized void displayErrorMessage(String message) {
        if (uiManager != null && rateLimit()) {
            uiManager.displayErrorMessage(message);
        }
    }

    /**
     * Limits the rate at which messages can be displayed.
     *
     * @return True if a message can be displayed, false if rate-limited
     */
    private static synchronized boolean rateLimit() {
        if (lastMessage == null || lastMessage.plus(MINIMUM_WAIT_DURATION).isBefore(Instant.now())) {
            lastMessage = Instant.now();
            return true;
        }
        return false;
    }

    /**
     * Displays an informational message to the user.
     *
     * @param message The information message to display
     */
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

    /**
     * Opens a folder in the system's file explorer.
     *
     * @param path The folder to open
     */
    public static void openFolder(File path) {
        if (uiManager != null)
            uiManager.openFolder(path);
    }

    /**
     * Registers a task with the UI handler.
     *
     * @param message The task message
     * @param active Whether the task is active
     * @return A Closeable that can be used to unregister the task
     */
    public static Closeable registerTask(String message, boolean active) {
        var task = new CloseableTask(message, active);
        synchronized (activeTasks) {
            activeTasks.add(task);
        }
        updateTooltip();
        return task;
    }

    /**
     * Checks if there is a long-running active task.
     *
     * @return True if there is a long-running active task, false otherwise
     */
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

    /**
     * Gets the message of the currently active task.
     *
     * @return The active task message, or null if no active task
     */
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

    /**
     * Updates the tooltip text in the UI.
     */
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

    /**
     * Removes a task from the active tasks list.
     *
     * @param task The task to remove
     */
    private static void removeTask(CloseableTask task) {
        synchronized (activeTasks) {
            activeTasks.remove(task);
        }
        updateTooltip();
    }

    /**
     * Opens a URI in the system's default browser.
     *
     * @param uri The URI to open
     */
    public static void openUri(URI uri) {
        if (uiManager != null) {
            uiManager.openUri(uri);
        }
    }

    /**
     * A task that can be closed to remove it from the active tasks list.
     */
    @Getter
    private static class CloseableTask implements Closeable {
        private final String message;
        private final boolean active;
        private final Instant started;

        /**
         * Creates a new closeable task.
         *
         * @param message The task message
         * @param active Whether the task is active
         */
        private CloseableTask(String message, boolean active) {
            this.message = message;
            this.active = active;
            this.started = Instant.now();
        }

        /**
         * Closes the task, removing it from the active tasks list.
         */
        @Override
        public void close() throws IOException {
            removeTask(this);
        }
    }
}
