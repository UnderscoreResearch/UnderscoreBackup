package com.underscoreresearch.backup.file;

/**
 * Interface for scheduling file scanning operations.
 * Provides methods to start, stop, and wait for completion of scheduled scans.
 */
public interface ScannerScheduler {
    /**
     * Starts the scanner scheduler.
     * Begins scheduling and executing file scanning operations.
     */
    void start();

    /**
     * Shuts down the scanner scheduler.
     * Stops scheduling and executing file scanning operations.
     */
    void shutdown();

    /**
     * Waits for all scheduled scanning operations to complete.
     * Blocks until all ongoing scans are finished.
     */
    void waitForCompletion();
}
