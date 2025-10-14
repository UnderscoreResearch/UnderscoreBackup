package com.underscoreresearch.backup.file.changepoller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Interface for polling file system changes.
 * Implementations of this interface monitor the file system for changes
 * and provide methods to register paths to watch and fetch changed paths.
 */
public interface FileChangePoller {
    /**
     * Register paths to be monitored for changes.
     *
     * @param paths List of paths to monitor
     * @throws IOException If there's an error registering the paths
     */
    void registerPaths(List<Path> paths) throws IOException;

    /**
     * Fetch paths that have changed since the last call.
     *
     * @return List of changed paths as strings
     * @throws IOException If there's an error fetching the paths
     * @throws OverflowException If there are too many changes to report
     */
    List<String> fetchPaths() throws IOException, OverflowException;

    /**
     * Close the poller and release any resources.
     *
     * @throws IOException If there's an error closing the poller
     */
    void close() throws IOException;

    /**
     * Exception thrown when there are too many changes to report.
     */
    class OverflowException extends Exception {
    }
}
