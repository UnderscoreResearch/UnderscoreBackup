package com.underscoreresearch.backup.file;

import java.io.IOException;

/**
 * Interface for watching file system changes.
 * Provides methods to start and stop monitoring for file changes.
 */
public interface FileChangeWatcher {
    /**
     * Starts watching for file system changes.
     *
     * @throws IOException if an I/O error occurs while setting up the watcher
     */
    void start() throws IOException;

    /**
     * Stops watching for file system changes.
     *
     * @throws IOException if an I/O error occurs while stopping the watcher
     */
    void stop() throws IOException;

    /**
     * Checks if the watcher is currently active.
     *
     * @return true if the watcher is active, false otherwise
     */
    boolean active();
}
