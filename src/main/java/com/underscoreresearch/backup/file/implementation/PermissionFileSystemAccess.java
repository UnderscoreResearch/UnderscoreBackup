package com.underscoreresearch.backup.file.implementation;

import java.io.File;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.underscoreresearch.backup.file.FilePermissionManager;
import com.underscoreresearch.backup.model.BackupFile;

@Slf4j
@RequiredArgsConstructor
public class PermissionFileSystemAccess extends FileSystemAccessImpl {
    private final FilePermissionManager permissionManager;

    @Override
    protected BackupFile createBackupFile(String path, File file) {
        return BackupFile.builder()
                .path(path)
                .length(file.length())
                .lastChanged(file.lastModified())
                .permissions(permissionManager.getPermissions(file.toPath()))
                .build();
    }

    @Override
    public void finalFileCompletion(BackupFile backupFile, File file) {
        permissionManager.setPermissions(file.toPath(), backupFile.getPermissions());
    }
}
