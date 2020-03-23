package com.underscoreresearch.backup.file;

import com.underscoreresearch.backup.model.BackupFile;

import java.io.IOException;
import java.util.Set;

public interface FileSystemAccess {
    Set<BackupFile> directoryFiles(String path);

    int readData(String path, byte[] buffer, long offset, int length) throws IOException;

    void writeData(String path, byte[] buffer, long offset, int length) throws IOException;

    void truncate(String path, long length) throws IOException;
}
