package com.underscoreresearch.backup.manifest;

import java.io.IOException;
import java.util.List;

import com.underscoreresearch.backup.model.BackupFile;

public interface BackupContentsAccess {
    List<BackupFile> directoryFiles(String path) throws IOException;

    String directoryPermissions(String path) throws IOException;
}