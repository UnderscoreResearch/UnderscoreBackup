package com.underscoreresearch.backup.manifest;

import java.io.IOException;
import java.util.List;

public interface BaseManifestManager {
    void addLogEntry(String type, String jsonDefinition);

    List<String> getExistingLogs() throws IOException;

    void deleteLogFiles(List<String> file) throws IOException;

    void flushLog() throws IOException;

    void initialize(LogConsumer logConsumer, boolean immediate) throws IOException;

    void validateIdentity();

    void shutdown() throws IOException;
}