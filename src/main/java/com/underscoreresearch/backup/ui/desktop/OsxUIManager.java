package com.underscoreresearch.backup.ui.desktop;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * A macOS-specific UI manager implementation.
 * This class extends FileUIManager to provide macOS-specific functionality
 * for opening files and folders using the 'open' command.
 */
@Slf4j
public class OsxUIManager extends FileUIManager {
    /**
     * Opens a file, folder, or URL using the macOS 'open' command.
     *
     * @param path The path or URL to open
     */
    @Override
    protected void openString(String path) {
        try {
            Runtime.getRuntime().exec(new String[]{"open", path});
        } catch (IOException e) {
            log.warn("Failed to open folder \"{}\"", path, e);
        }
    }
}
