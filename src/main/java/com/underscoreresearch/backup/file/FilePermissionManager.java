package com.underscoreresearch.backup.file;

import java.nio.file.Path;

/**
 * Interface for managing file permissions.
 * Provides methods to get and set file permissions in a platform-independent way.
 */
public interface FilePermissionManager {
    /**
     * Gets the permissions for a file or directory.
     *
     * @param path The path to the file or directory
     * @return A string representation of the permissions
     */
    String getPermissions(Path path);

    /**
     * Sets the permissions for a file or directory.
     *
     * @param path The path to the file or directory
     * @param permissions A string representation of the permissions to set
     */
    void setPermissions(Path path, String permissions);
}
