package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a file that has been updated.
 * Contains information about a file's path and when it was last updated.
 */

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupUpdatedFile {
    /**
     * The path of the updated file.
     */
    private String path;
    
    /**
     * The timestamp when the file was last updated.
     */
    private long lastUpdated;
}
