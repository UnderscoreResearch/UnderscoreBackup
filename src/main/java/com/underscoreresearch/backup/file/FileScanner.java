package com.underscoreresearch.backup.file;

import com.underscoreresearch.backup.model.BackupSet;

import java.io.IOException;

/**
 * Interface for scanning files for backup.
 * Provides methods to start and stop scanning files in a backup set.
 */
public interface FileScanner {
    /**
     * Starts scanning files in the specified backup set.
     *
     * @param backupSet The backup set to scan
     * @return true if scanning was started successfully, false otherwise
     * @throws IOException if an I/O error occurs during scanning
     */
    boolean startScanning(BackupSet backupSet) throws IOException;

    /**
     * Shuts down the file scanner.
     * Stops any ongoing scanning operations.
     */
    void shutdown();
}
