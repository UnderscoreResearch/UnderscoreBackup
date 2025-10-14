package com.underscoreresearch.backup.manifest.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.underscoreresearch.backup.model.BackupActivePath;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing the log information for an active path to be pushed to the backup service.
 * Contains information about the set, path, and active path details.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PushActivePath {
    /**
     * The identifier of the backup set.
     */
    private String setId;
    
    /**
     * The path being pushed.
     */
    private String path;
    
    /**
     * The active path details.
     */
    private BackupActivePath activePath;
}
