package com.underscoreresearch.backup.manifest.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.NavigableSet;

/**
 * Model class representing a directory in the backup in the backup log.
 * Contains information about the directory path, permissions, and files.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackupDirectory {
    /**
     * The path of the directory.
     */
    private String path;
    
    /**
     * Timestamp when the directory was added.
     */
    private Long added;
    
    /**
     * Permissions of the directory.
     */
    private String permissions;
    
    /**
     * Set of files in the directory.
     */
    private NavigableSet<String> files;
    
    /**
     * Timestamp when the directory was deleted, or null if not deleted.
     */
    private Long deleted;
}
