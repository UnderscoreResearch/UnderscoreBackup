package com.underscoreresearch.backup.cli.ui;

import static com.underscoreresearch.backup.utils.SerializationUtils.BACKUP_CONFIGURATION_WRITER;

import javax.imageio.ImageIO;
import java.awt.AWTError;
import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.File;
import java.io.IOException;
import java.net.URI;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.SystemUtils;

import com.underscoreresearch.backup.cli.web.ConfigurationPost;
import com.underscoreresearch.backup.cli.web.WebServer;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import com.underscoreresearch.backup.model.BackupConfiguration;

@Slf4j
public class AwtUIManager implements UIManager {
    public static final String STARTED_IN_BACKGROUND_MESSAGE = "Started in the background";
    private TrayIcon trayIcon;

    public AwtUIManager() {
        try {
            if (SystemTray.isSupported()) {
                EventQueue.invokeLater(this::createAndShowGUI);
            }
        } catch (AWTError | NoClassDefFoundError ignored) {
        }
    }

    private void createAndShowGUI() {
        final PopupMenu popup = new PopupMenu();
        try {
            trayIcon = new TrayIcon(ImageIO.read(UIHandler.class.getClassLoader().getResource("trayicon.png")));
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

        MenuItem configure = new MenuItem("Open...");
        configure.addActionListener((e) -> launchConfig());

        //Add components to pop-up menu
        popup.add(configure);
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener((e) -> launchConfig());

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Could not show tray icon", e);
            return;
        }

        displayStartedMessage();
    }

    protected void displayStartedMessage() {
        displayInfoMessage(STARTED_IN_BACKGROUND_MESSAGE);
    }

    protected void launchConfig() {
        InstanceFactory.getInstance(WebServer.class).launchPage();
    }

    @Override
    public void displayErrorMessage(String message) {
        if (trayIcon != null) {
            EventQueue.invokeLater(() -> trayIcon.displayMessage("Underscore Backup", message,
                    TrayIcon.MessageType.ERROR));
        }
    }

    @Override
    public void displayInfoMessage(String message) {
        if (trayIcon != null) {
            EventQueue.invokeLater(() -> trayIcon.displayMessage("Underscore Backup", message,
                    TrayIcon.MessageType.INFO));
        }
    }

    @Override
    public void openFolder(File path) {
        if (Desktop.getDesktop() != null) {
            try {
                Desktop.getDesktop().open(path);
            } catch (IOException e) {
                log.warn("Failed to open folder \"{}\"", path.toString(), e);
            }
        }
    }

    @Override
    public void openUri(URI uri) {
        if (Desktop.getDesktop() != null) {
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException e) {
                log.warn("Failed to open uri \"{}\"", uri.toString(), e);
            }
        }
    }

    @Override
    public void setTooltip(String message) {
        if (trayIcon != null) {
            EventQueue.invokeLater(() -> trayIcon.setToolTip(message));
        }
    }
}
