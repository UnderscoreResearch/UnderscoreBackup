package com.underscoreresearch.backup.file.implementation;

import java.io.File;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.FilePermissionManager;
import com.underscoreresearch.backup.file.PathNormalizer;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
@RequiredArgsConstructor
public class PermissionFileSystemAccess extends FileSystemAccessImpl {
    private final FilePermissionManager permissionManager;

    @Override
    public void populatePermissions(BackupFile backupFile) {
        backupFile.setPermissions(permissionManager.getPermissions(
                Path.of(PathNormalizer.physicalPath(backupFile.getPath()))));
    }

    @Override
    public void finalFileCompletion(BackupFile backupFile, File file) {
        permissionManager.setPermissions(file.toPath(), backupFile.getPermissions());
    }
}
