package com.underscoreresearch.backup.file.changepoller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FileChangePoller {
    void registerPaths(List<Path> paths) throws IOException;

    List<String> fetchPaths() throws IOException, OverflowException;

    void close() throws IOException;

    class OverflowException extends Exception {
    }
}
