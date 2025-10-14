package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a simplified external representation of a backup file.
 * This class contains only the essential metadata about a backup file without the storage details,
 * making it suitable for external interfaces or lightweight representations.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class ExternalBackupFile {
    /**
     * Timestamp when the file was added to the backup.
     */
    private Long added;
    
    /**
     * Timestamp when the file was last changed.
     */
    private Long lastChanged;
    
    /**
     * Timestamp when the file was deleted, or null if not deleted.
     */
    private Long deleted;
    
    /**
     * Size of the file in bytes.
     */
    private Long length;
    
    /**
     * Path of the file.
     */
    private String path;

    /**
     * Constructs an ExternalBackupFile from a BackupFile.
     * Extracts only the essential metadata from the BackupFile, excluding storage details.
     * 
     * @param file The BackupFile to extract metadata from
     */
    public ExternalBackupFile(BackupFile file) {
        this.added = file.getAdded();
        this.lastChanged = file.getLastChanged();
        this.deleted = file.getDeleted();
        this.length = file.getLength();
        this.path = file.getPath();
    }
}
