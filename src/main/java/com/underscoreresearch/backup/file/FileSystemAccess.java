package com.underscoreresearch.backup.file;

import java.io.IOException;
import java.util.Set;

import com.underscoreresearch.backup.model.BackupFile;

public interface FileSystemAccess {
    Set<BackupFile> directoryFiles(String path);

    void populatePermissions(BackupFile backupFile) throws IOException;

    int readData(String path, byte[] buffer, long offset, int length) throws IOException;

    void writeData(String path, byte[] buffer, long offset, int length) throws IOException;

    void completeFile(BackupFile file, String path, long length) throws IOException;

    void delete(String path) throws IOException;
}
