package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.model.BackupFile;

import java.util.function.Consumer;

public interface DownloadScheduler {
    void scheduleDownload(BackupFile file, String destination, String password);

    void addCompletionCallback(Consumer<String> callback);

    void removeCompletionCallback(Consumer<String> callback);

    void shutdown();

    void waitForCompletion();
}
