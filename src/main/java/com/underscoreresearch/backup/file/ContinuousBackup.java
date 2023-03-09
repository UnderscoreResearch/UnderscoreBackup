package com.underscoreresearch.backup.file;

public interface ContinuousBackup {
    void start();

    void shutdown();

    void signalChanged();
}
