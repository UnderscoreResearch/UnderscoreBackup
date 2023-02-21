package com.underscoreresearch.backup.file;

import java.io.IOException;

public interface FileChangeWatcher {
    void start() throws IOException;

    void stop() throws IOException;

    boolean active();
}
