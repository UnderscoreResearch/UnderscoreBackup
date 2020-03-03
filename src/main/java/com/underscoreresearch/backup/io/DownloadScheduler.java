package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.model.BackupFile;

public interface DownloadScheduler {
    void scheduleDownload(BackupFile file, String destination);

    void shutdown();

    void waitForCompletion();
}
