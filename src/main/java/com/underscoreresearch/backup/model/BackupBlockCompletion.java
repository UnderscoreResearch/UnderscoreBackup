package com.underscoreresearch.backup.model;

import java.util.List;

/**
 * Interface for handling the completion of backup block operations.
 * Provides a callback mechanism for when block operations are completed.
 */

public interface BackupBlockCompletion {
    /**
     * Called when a block operation is completed.
     * 
     * @param locations The list of locations where the block was stored
     */
    void completed(List<BackupLocation> locations);
}
