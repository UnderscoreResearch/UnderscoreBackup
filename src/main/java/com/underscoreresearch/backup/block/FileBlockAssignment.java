package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupBlockCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSet;

/**
 * Interface for assigning blocks to backup files.
 * Handles the process of breaking files into blocks for storage.
 */
public interface FileBlockAssignment {
    /**
     * Assign blocks to a backup file.
     * 
     * @param set The backup set containing the file
     * @param file The file to assign blocks to
     * @param completionFuture Callback for when block assignment is complete
     * @return true if the assignment was successful, false otherwise
     */
    boolean assignBlocks(BackupSet set, BackupFile file, BackupBlockCompletion completionFuture);

    /**
     * Flush any pending block assignments.
     * Ensures all assignments are committed to storage.
     */
    void flushAssignments();
}
