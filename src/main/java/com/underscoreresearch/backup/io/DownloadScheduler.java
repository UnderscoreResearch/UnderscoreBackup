package com.underscoreresearch.backup.io;

import java.util.function.Consumer;

import com.underscoreresearch.backup.model.BackupFile;

public interface DownloadScheduler {
    void scheduleDownload(BackupFile file, String destination, String password);

    void addCompletionCallback(Consumer<String> callback);

    void removeCompletionCallback(Consumer<String> callback);

    void shutdown();

    void waitForCompletion();
}
