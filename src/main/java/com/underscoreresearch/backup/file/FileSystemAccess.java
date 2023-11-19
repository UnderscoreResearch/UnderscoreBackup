package com.underscoreresearch.backup.file;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import com.underscoreresearch.backup.model.BackupFile;

public interface FileSystemAccess {
    Set<BackupFile> directoryFiles(String path);


    void applyPermissions(File path, String permissions) throws IOException;

    int readData(String path, byte[] buffer, long offset, int length) throws IOException;

    void writeData(String path, byte[] buffer, long offset, int length) throws IOException;

    void completeFile(BackupFile file, String path, long length) throws IOException;

    String extractPermissions(String path) throws IOException;

    void delete(String path) throws IOException;
}
