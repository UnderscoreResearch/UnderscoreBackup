package com.underscoreresearch.backup.cli;

import java.awt.*;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;

import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.cli.web.WebServer;
import com.underscoreresearch.backup.configuration.InstanceFactory;

@Slf4j
public class UIManager {
    public static void setup() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }

    private static void createAndShowGUI() {
        if (!SystemTray.isSupported()) {
            return;
        }
        final PopupMenu popup = new PopupMenu();
        final TrayIcon trayIcon;
        try {
            trayIcon = new TrayIcon(ImageIO.read(UIManager.class.getClassLoader().getResource("trayicon.png")));
        } catch (IOException exc) {
            log.error("Failed to load tray icon resource", exc);
            return;
        }
        final SystemTray tray = SystemTray.getSystemTray();

        // Create a pop-up menu components
        MenuItem exitItem = new MenuItem("Quit");
        exitItem.addActionListener((e) -> {
            System.exit(0);
        });

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

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("Could not show tray icon", e);
            return;
        }
    }
}
