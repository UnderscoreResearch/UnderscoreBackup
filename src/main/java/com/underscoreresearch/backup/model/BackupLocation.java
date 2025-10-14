package com.underscoreresearch.backup.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a location in the backup system.
 * Contains information about when the location was created and its file parts.
 */

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
public class BackupLocation {
    /**
     * Timestamp when this location was created.
     */
    private long creation;
    
    /**
     * List of file parts in this location.
     */
    private List<BackupFilePart> parts;
}
