package com.underscoreresearch.backup.cli;

import static com.underscoreresearch.backup.io.IOUtils.createDirectory;
import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;

import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.cli.web.WebServer;
import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class UIManager {

    private static final Duration MINIMUM_WAIT_DURATION = Duration.ofSeconds(20);
    private static final List<CloseableTask> activeTasks = new ArrayList<>();
    private static TrayIcon trayIcon;
    private static Instant lastMessage;

    public static void setup() {
        if (SystemUtils.IS_OS_MAC_OSX) {
            updateTooltip();
        } else {
            if (trayIcon == null) {
                EventQueue.invokeLater(UIManager::createAndShowGUI);
            }
        }
    }

    private static void createAndShowGUI() {
        if (!SystemTray.isSupported()) {
            return;
        }
        final PopupMenu popup = new PopupMenu();
        try {
            trayIcon = new TrayIcon(ImageIO.read(UIManager.class.getClassLoader().getResource("trayicon.png")));
        } catch (IOException exc) {
            log.error("Failed to load tray icon resource", exc);
            return;
        }
        final SystemTray tray = SystemTray.getSystemTray();

        // Create a pop-up menu components
        MenuItem exitItem;
        if (SystemUtils.IS_OS_MAC_OSX) {
            exitItem = new MenuItem("Stop");
            exitItem.addActionListener((e) -> {
                try {
                    ConfigurationPost.updateConfiguration(
                            BACKUP_CONFIGURATION_WRITER
                                    .writeValueAsString(InstanceFactory.getInstance(BackupConfiguration.class)), true, false, false);
                    InstanceFactory.reloadConfiguration(null);
                } catch (IOException ex) {
                    log.error("Failed to stop backup", ex);
                }
            });
        } else {
            exitItem = new MenuItem("Quit");
            exitItem.addActionListener((e) -> {
                System.exit(0);
            });
        }

        MenuItem configure = new MenuItem("Configure...");
        configure.addActionListener((e) -> {
            InstanceFactory.getInstance(WebServer.class).launchPage();
        });

        //Add components to pop-up menu
        popup.add(configure);
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener((e) -> {
            InstanceFactory.getInstance(WebServer.class).launchPage();
        });
        updateTooltipTrayIcon();

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Could not show tray icon", e);
            return;
        }

        displayInfoMessage("Started in the background");
    }

    public static synchronized void displayErrorMessage(String message) {
        if (rateLimit()) {
            if (SystemUtils.IS_OS_MAC_OSX) {
                writeOsxNotification("error", message);
            } else if (trayIcon != null) {
                trayIcon.displayMessage("Underscore Backup", message,
                        TrayIcon.MessageType.ERROR);
            }
        }
    }

    private static synchronized void writeOsxNotification(String location, String message) {
        File parentDirectory = new File(new File(InstanceFactory.getInstance(CommandLineModule.MANIFEST_LOCATION)), "notifications");
        createDirectory(parentDirectory);
        File file = new File(parentDirectory, location);
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(message);
        } catch (IOException e) {
            log.warn("Failed to write notification message", e);
        }
        try {
            ConfigurationPost.setOwnerOnlyPermissions(file);
        } catch (IOException e) {
            log.warn("Failed to make notification message read only", e);
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
        if (rateLimit()) {
            if (InstanceFactory.hasConfiguration(true)) {
                BackupConfiguration config = InstanceFactory.getInstance(BackupConfiguration.class);
                if (config.getManifest() != null && config.getManifest().getHideNotifications() != null
                        && config.getManifest().getHideNotifications()) {
                    return;
                }
            }
            if (SystemUtils.IS_OS_MAC_OSX) {
                writeOsxNotification("notification", message);
            } else if (trayIcon != null) {
                trayIcon.displayMessage("Underscore Backup", message,
                        TrayIcon.MessageType.INFO);
            }
        }
    }

    public static void openFolder(File path) {
        try {
            if (SystemUtils.IS_OS_MAC_OSX) {
                Runtime.getRuntime().exec(new String[]{"open", path.toString()});
            } else if (SystemUtils.IS_OS_WINDOWS) {
                if (Desktop.getDesktop() != null) {
                    Desktop.getDesktop().open(path);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to open folder {}", path.toString(), e);
        }
    }

    public static Closeable registerTask(String message) {
        var task = new CloseableTask(message);
        synchronized (activeTasks) {
            activeTasks.add(task);
        }
        updateTooltip();
        return task;
    }

    private static void updateTooltipTrayIcon() {
        synchronized (activeTasks) {
            String message;
            if (activeTasks.size() == 0) {
                message = "Underscore Backup - Idle";
            } else {
                message = "Underscore Backup - " + activeTasks.get(activeTasks.size() - 1).getMessage();
            }
            trayIcon.setToolTip(message);
        }
    }

    private static void updateTooltip() {
        if (trayIcon != null) {
            EventQueue.invokeLater(UIManager::updateTooltipTrayIcon);
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            synchronized (activeTasks) {
                String message;
                if (activeTasks.size() == 0) {
                    message = "Underscore Backup - Idle";
                } else {
                    message = "Underscore Backup - " + activeTasks.get(activeTasks.size() - 1).getMessage();
                }
                writeOsxNotification("tooltip", message);
            }
        }
    }

    private static void removeTask(CloseableTask task) {
        synchronized (activeTasks) {
            activeTasks.remove(task);
        }
        updateTooltip();
    }

    @AllArgsConstructor
    private static class CloseableTask implements Closeable {
        @Getter
        private String message;

        @Override
        public void close() throws IOException {
            removeTask(this);
        }
    }
}
