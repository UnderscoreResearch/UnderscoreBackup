package com.underscoreresearch.backup.file.implementation;

import java.io.File;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.FilePermissionManager;
import com.underscoreresearch.backup.file.PathNormalizer;

@Slf4j
@RequiredArgsConstructor
public class PermissionFileSystemAccess extends FileSystemAccessImpl {
    private final FilePermissionManager permissionManager;

    @Override
    public String extractPermissions(String path) {
        return permissionManager.getPermissions(
                Path.of(PathNormalizer.physicalPath(path)));
    }

    @Override
    public void applyPermissions(File path, String permissions) {
        permissionManager.setPermissions(
                path.toPath(), permissions);
    }
}
