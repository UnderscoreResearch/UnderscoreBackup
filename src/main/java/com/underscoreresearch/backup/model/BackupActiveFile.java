package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a file that is actively being tracked by the backup system.
 * Contains information about the file path and its current status in the backup process.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(exclude = "status")
public class BackupActiveFile {
    /**
     * The path of the file being tracked.
     */
    @Setter(AccessLevel.PRIVATE)
    private String path;
    
    /**
     * The current status of the file in the backup process.
     */
    private BackupActiveStatus status;

    /**
     * Constructor that initializes a BackupActiveFile with just a path.
     * 
     * @param path The path of the file to track
     */
    public BackupActiveFile(String path) {
        this.path = path;
    }
}
