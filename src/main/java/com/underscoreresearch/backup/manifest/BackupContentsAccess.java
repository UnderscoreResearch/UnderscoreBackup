package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.model.BackupFile;

import java.io.IOException;
import java.util.List;

public interface BackupContentsAccess {
    List<BackupFile> directoryFiles(String path) throws IOException;
}