package com.underscoreresearch.backup.ui.desktop;

import java.io.File;
import java.net.URI;

/**
 * Interface for platform-specific UI management.
 * Implementations of this interface provide platform-specific ways to
 * display messages, open files and folders, and manage UI elements.
 */
public interface UIManager {
    /**
     * Displays an error message to the user.
     *
     * @param message The error message to display
     */
    void displayErrorMessage(String message);

    /**
     * Displays an informational message to the user.
     *
     * @param message The information message to display
     */
    void displayInfoMessage(String message);

    /**
     * Opens a folder in the system's file explorer.
     *
     * @param path The folder to open
     */
    void openFolder(File path);

    /**
     * Opens a URI in the system's default browser.
     *
     * @param uri The URI to open
     */
    void openUri(URI uri);

    /**
     * Sets the tooltip text for the application's UI.
     *
     * @param message The tooltip message
     */
    void setTooltip(String message);
}
