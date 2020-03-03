package com.underscoreresearch.backup.manifest;

import java.io.IOException;

public interface ManifestManager {
    void addLogEntry(String type, String jsonDefinition);

    void replayLog(LogConsumer consumer) throws IOException;

    void initialize() throws IOException;

    BackupContentsAccess backupContents(Long timestamp) throws IOException;

    void shutdown() throws IOException;
}
