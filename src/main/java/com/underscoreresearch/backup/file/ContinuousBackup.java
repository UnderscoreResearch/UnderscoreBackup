package com.underscoreresearch.backup.file;

import java.io.IOException;

public interface ContinuousBackup {
    void start();
    void shutdown();
    void signalChanged();
}
