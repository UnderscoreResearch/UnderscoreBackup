package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Represents a retention policy for backup files.
 * Contains information about how long to keep deleted files, how frequently to keep versions,
 * and how many versions to keep.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRetention {
    /**
     * How long to keep deleted files.
     */
    private BackupTimespan retainDeleted;
    
    /**
     * Default frequency for keeping versions.
     */
    private BackupTimespan defaultFrequency;
    
    /**
     * Additional retention policies for older files.
     */
    private TreeSet<BackupRetentionAdditional> older;

    /**
     * Maximum number of versions to keep.
     */
    private Integer maximumVersions;

    /**
     * Checks if deleted files should be removed immediately.
     * 
     * @return True if deleted files should be removed immediately, false otherwise
     */
    @JsonIgnore
    public boolean deletedImmediate() {
        BackupTimespan deletedTimespan = Optional.ofNullable(retainDeleted).orElse(new BackupTimespan());
        return deletedTimespan.isImmediate();
    }

    /**
     * Checks if a file should be kept based on the retention policy.
     * 
     * @param file The file to check
     * @param previousFile The previous version of the file
     * @param deleted Whether the file is deleted
     * @return True if the file should be kept, false otherwise
     */
    public boolean keepFile(BackupFile file, BackupFile previousFile, boolean deleted) {
        BackupTimespan deletedTimespan = Optional.ofNullable(retainDeleted).orElse(new BackupTimespan());
        if (deleted) {
            if (deletedTimespan.isImmediate())
                return false;

            if (!deletedTimespan.isForever()
                    && file.deletedToTime().isBefore(deletedTimespan.toTime())) {
                return false;
            }
        }

        if (previousFile == null) {
            return true;
        }

        LocalDateTime fileInstant = file.addedToTime();

        BackupTimespan frequency = defaultFrequency;
        if (older != null) {
            for (BackupRetentionAdditional ta : older) {
                if (fileInstant.isBefore(ta.getValidAfter().toTime()))
                    frequency = ta.getFrequency();
                else
                    break;
            }
        }

        if (frequency == null)
            frequency = new BackupTimespan();

        return frequency.isImmediate() || (!frequency.isForever() && !frequency.toTime(previousFile.addedToTime()).isBefore(fileInstant));
    }
}
