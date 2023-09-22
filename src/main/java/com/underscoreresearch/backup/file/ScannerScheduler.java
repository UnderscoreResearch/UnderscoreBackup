package com.underscoreresearch.backup.file;

public interface ScannerScheduler {
    void start();

    void shutdown();

    void waitForCompletion();
}
