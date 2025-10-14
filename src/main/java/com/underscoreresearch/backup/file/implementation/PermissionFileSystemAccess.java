package com.underscoreresearch.backup.file.implementation;

import com.underscoreresearch.backup.file.FilePermissionManager;
import com.underscoreresearch.backup.file.PathNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Path;

/**
 * Extension of FileSystemAccessImpl that adds permission management.
 * Uses a FilePermissionManager to extract and apply file permissions.
 */
@Slf4j
@RequiredArgsConstructor
public class PermissionFileSystemAccess extends FileSystemAccessImpl {
    private final FilePermissionManager permissionManager;

    /**
     * Extracts permissions from a file or directory.
     *
     * @param path The path to the file or directory
     * @return A string representation of the permissions
     */
    @Override
    public String extractPermissions(String path) {
        return permissionManager.getPermissions(
                Path.of(PathNormalizer.physicalPath(path)));
    }

    /**
     * Applies permissions to a file or directory.
     *
     * @param path The file or directory
     * @param permissions A string representation of the permissions
     */
    @Override
    public void applyPermissions(File path, String permissions) {
        permissionManager.setPermissions(
                path.toPath(), permissions);
    }
}
