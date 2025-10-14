package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.model.BackupFile;

import java.io.IOException;
import java.util.List;

/**
 * Interface for accessing backup contents, providing methods to retrieve files and permissions.
 */
public interface BackupContentsAccess {
    /**
     * Get a list of files in a specific directory path.
     * 
     * @param path The directory path to list files from
     * @return List of backup files in the directory
     * @throws IOException If there's an error accessing the directory
     */
    List<BackupFile> directoryFiles(String path) throws IOException;

    /**
     * Get the permissions for a specific directory path.
     * 
     * @param path The directory path to get permissions for
     * @return String representation of the directory permissions
     * @throws IOException If there's an error accessing the directory permissions
     */
    String directoryPermissions(String path) throws IOException;
}
