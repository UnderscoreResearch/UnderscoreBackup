package com.underscoreresearch.backup.file;

/**
 * Interface for managing continuous backup operations.
 * Provides methods to start, stop, and signal changes for continuous backup processes.
 */
public interface ContinuousBackup {
    /**
     * Starts the continuous backup process.
     */
    void start();

    /**
     * Shuts down the continuous backup process.
     */
    void shutdown();

    /**
     * Signals that a change has occurred that requires backup attention.
     */
    void signalChanged();
}
