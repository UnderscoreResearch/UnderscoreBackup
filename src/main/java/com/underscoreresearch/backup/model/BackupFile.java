package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static com.underscoreresearch.backup.file.PathNormalizer.PATH_SEPARATOR;

/**
 * Represents a file in the backup system.
 * Contains information about a file's path, timestamps, size, permissions, and storage locations.
 */

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupFile implements Comparable<BackupFile> {
    /**
     * Timestamp when the file was added to the backup.
     */
    private Long added;
    
    /**
     * Timestamp when the file was deleted, or null if not deleted.
     */
    private Long deleted;
    
    /**
     * Timestamp when the file was last changed.
     */
    private Long lastChanged;
    
    /**
     * Size of the file in bytes.
     */
    private Long length;
    
    /**
     * Path of the file.
     */
    private String path;
    
    /**
     * File permissions in string format.
     */
    private String permissions;
    
    /**
     * List of locations where the file is stored.
     */
    private List<BackupLocation> locations;

    /**
     * Compares this file to another file for sorting.
     * Files are sorted by path, and then by added timestamp.
     * 
     * @param backupFile The file to compare to
     * @return A negative integer, zero, or a positive integer as this file is less than, equal to, or greater than the specified file
     */
    @Override
    public int compareTo(BackupFile backupFile) {
        int pathCompare = path.compareTo(backupFile.path);
        if (pathCompare != 0)
            return pathCompare;
        if (Objects.equals(added, backupFile.added))
            return 0;
        if (added == null)
            return -1;
        if (backupFile.added == null)
            return 1;
        return added.compareTo(backupFile.added);
    }

    /**
     * Converts the added timestamp to a LocalDateTime.
     * 
     * @return The added timestamp as a LocalDateTime, or null if not set
     */
    @JsonIgnore
    public LocalDateTime addedToTime() {
        if (added != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(added), OffsetDateTime.now().getOffset());
        }
        return null;
    }

    /**
     * Converts the deleted timestamp to a LocalDateTime.
     * 
     * @return The deleted timestamp as a LocalDateTime, or null if not set
     */
    @JsonIgnore
    public LocalDateTime deletedToTime() {
        if (deleted != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(deleted), OffsetDateTime.now().getOffset());
        }
        return null;
    }

    /**
     * Converts the lastChanged timestamp to a LocalDateTime.
     * 
     * @return The lastChanged timestamp as a LocalDateTime, or null if not set
     */
    @JsonIgnore
    public LocalDateTime lastChangedToTime() {
        if (lastChanged != null) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastChanged), OffsetDateTime.now().getOffset());
        }
        return null;
    }

    /**
     * Checks if this file is a directory.
     * 
     * @return True if this file is a directory, false otherwise
     */
    @JsonIgnore
    public boolean isDirectory() {
        return path.endsWith(PATH_SEPARATOR);
    }
}
