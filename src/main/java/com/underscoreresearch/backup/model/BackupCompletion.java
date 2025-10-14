package com.underscoreresearch.backup.model;

/**
 * Interface for handling the completion of backup operations.
 * Provides a callback mechanism for when backup operations are completed.
 */

public interface BackupCompletion {
    /**
     * Called when a backup operation is completed.
     * 
     * @param success Whether the operation was successful
     */
    void completed(boolean success);
}
