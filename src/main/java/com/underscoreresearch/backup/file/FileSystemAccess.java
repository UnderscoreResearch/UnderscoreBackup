package com.underscoreresearch.backup.file;

import java.io.IOException;
import java.util.Set;

import com.underscoreresearch.backup.model.BackupFile;

public interface FileSystemAccess {
    Set<BackupFile> directoryFiles(String path);

    int readData(String path, byte[] buffer, long offset, int length) throws IOException;

    void writeData(String path, byte[] buffer, long offset, int length) throws IOException;

    void truncate(String path, long length) throws IOException;

    void delete(String path) throws IOException;
}
