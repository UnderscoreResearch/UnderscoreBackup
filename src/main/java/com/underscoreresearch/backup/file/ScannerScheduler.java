package com.underscoreresearch.backup.file;

public interface ScannerScheduler {
    void start();
    void shutdown();
    boolean isRunning();
}
