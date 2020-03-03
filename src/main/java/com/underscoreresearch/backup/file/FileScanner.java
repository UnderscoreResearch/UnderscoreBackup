package com.underscoreresearch.backup.file;

import java.io.IOException;

import com.underscoreresearch.backup.model.BackupSet;

public interface FileScanner {
    boolean startScanning(BackupSet backupSet) throws IOException;

    void shutdown();
}
