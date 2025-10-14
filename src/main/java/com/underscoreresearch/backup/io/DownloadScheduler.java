package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.model.BackupFile;

import java.util.function.Consumer;

/**
 * Interface for scheduling downloads of backup files.
 * Provides methods for asynchronous download operations.
 */
public interface DownloadScheduler {
    /**
     * Schedule a download of a backup file.
     *
     * @param file The file to download
     * @param destination The destination path to save the file
     * @param password The password for decryption, if required
     */
    void scheduleDownload(BackupFile file, String destination, String password);

    /**
     * Add a callback to be notified when downloads complete.
     *
     * @param callback The callback to add
     */
    void addCompletionCallback(Consumer<String> callback);

    /**
     * Remove a previously added completion callback.
     *
     * @param callback The callback to remove
     */
    void removeCompletionCallback(Consumer<String> callback);

    /**
     * Shutdown the scheduler and release resources.
     */
    void shutdown();

    /**
     * Wait for all scheduled downloads to complete.
     */
    void waitForCompletion();
}
