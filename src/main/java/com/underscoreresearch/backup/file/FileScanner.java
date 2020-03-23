package com.underscoreresearch.backup.file;

import com.underscoreresearch.backup.model.BackupSet;

import java.io.IOException;

public interface FileScanner {
    boolean startScanning(BackupSet backupSet) throws IOException;

    void shutdown();
}
