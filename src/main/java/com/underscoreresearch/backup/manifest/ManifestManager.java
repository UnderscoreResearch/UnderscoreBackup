package com.underscoreresearch.backup.manifest;

import java.io.IOException;

import com.underscoreresearch.backup.file.MetadataRepository;

public interface ManifestManager {
    void addLogEntry(String type, String jsonDefinition);

    void replayLog(LogConsumer consumer) throws IOException;

    void flushLog() throws IOException;

    void optimizeLog(MetadataRepository existingRepository, LogConsumer logConsumer) throws IOException;

    void initialize(LogConsumer logConsumer, boolean immediate) throws IOException;

    void setDisabledFlushing(boolean disabledFlushing);

    BackupContentsAccess backupContents(Long timestamp, boolean includeDeleted) throws IOException;

    void shutdown() throws IOException;
}
