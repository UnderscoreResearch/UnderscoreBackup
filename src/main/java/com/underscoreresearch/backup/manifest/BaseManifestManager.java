package com.underscoreresearch.backup.manifest;

import java.io.IOException;

public interface BaseManifestManager {
    void addLogEntry(String type, String jsonDefinition);

    void syncLog() throws IOException;

    void initialize(LogConsumer logConsumer, boolean immediate) throws IOException;

    void validateIdentity();

    void waitUploads();

    void shutdown() throws IOException;
}