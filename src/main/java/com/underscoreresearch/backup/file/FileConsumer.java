package com.underscoreresearch.backup.file;

import com.underscoreresearch.backup.model.BackupCompletion;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupSet;

/**
 * Interface for consuming files during backup operations.
 * Provides methods to process files for backup and manage backup assignments.
 */
public interface FileConsumer {
    /**
     * Processes a file for backup.
     *
     * @param set The backup set to which the file belongs
     * @param file The file to be backed up
     * @param completionPromise Promise to be fulfilled when backup completes
     */
    void backupFile(BackupSet set, BackupFile file, BackupCompletion completionPromise);

    /**
     * Flushes any pending backup assignments.
     * Ensures that all assigned backup operations are processed.
     */
    void flushAssignments();
}
