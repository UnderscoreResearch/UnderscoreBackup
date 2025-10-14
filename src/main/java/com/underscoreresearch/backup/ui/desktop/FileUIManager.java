package com.underscoreresearch.backup.ui.desktop;

import com.underscoreresearch.backup.configuration.CommandLineModule;
import com.underscoreresearch.backup.configuration.InstanceFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.underscoreresearch.backup.ui.desktop.AwtUIManager.STARTED_IN_BACKGROUND_MESSAGE;
import static com.underscoreresearch.backup.io.IOUtils.createDirectory;

/**
 * A UI manager implementation that writes notifications to files.
 * This implementation is useful for headless environments or when
 * the application is running as a service.
 */
@Slf4j
public class FileUIManager implements UIManager {

    /**
     * Creates a new FileUIManager and displays a startup message.
     */
    public FileUIManager() {
        displayInfoMessage(STARTED_IN_BACKGROUND_MESSAGE);
    }

    /**
     * Displays an error message by writing it to an error file.
     *
     * @param message The error message to display
     */
    @Override
    public void displayErrorMessage(String message) {
        writeNotification("error", message);
    }

    /**
     * Writes a notification message to a file.
     *
     * @param location The file name to write to
     * @param message The message to write
     */
    private void writeNotification(String location, String message) {
        File parentDirectory = new File(InstanceFactory.getInstance(CommandLineModule.NOTIFICATION_LOCATION));
        createDirectory(parentDirectory, true);
        File file = new File(parentDirectory, location);
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(message);
        } catch (IOException e) {
            log.warn("Failed to write notification message", e);
        }
    }

    /**
     * Displays an informational message by writing it to an info file.
     *
     * @param message The information message to display
     */
    @Override
    public void displayInfoMessage(String message) {
        writeNotification("info", message);
    }

    /**
     * Records a request to open a folder by writing it to an open file.
     *
     * @param path The folder to open
     */
    @Override
    public void openFolder(File path) {
        openString(path.toString());
    }

    /**
     * Records a request to open a URI by writing it to an open file.
     *
     * @param uri The URI to open
     */
    @Override
    public void openUri(URI uri) {
        openString(uri.toString());
    }

    /**
     * Records a request to open a string by writing it to an open file.
     *
     * @param string The string to open
     */
    protected void openString(String string) {
        writeNotification("open", string);
    }

    /**
     * Sets the tooltip text by writing it to a tooltip file.
     *
     * @param message The tooltip message
     */
    @Override
    public void setTooltip(String message) {
        writeNotification("tooltip", message);
    }
}
