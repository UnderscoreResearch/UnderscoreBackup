package com.underscoreresearch.backup.model;

/**
 * Enum representing the status of a file in the active backup process.
 * Defines the possible states a file can be in during the backup process.
 */
public enum BackupActiveStatus {
    /**
     * The file is in the process of being backed up but is not yet complete.
     */
    INCOMPLETE,
    
    /**
     * The file is included in the backup.
     */
    INCLUDED,
    
    /**
     * The file is explicitly excluded from the backup.
     */
    EXCLUDED
}
