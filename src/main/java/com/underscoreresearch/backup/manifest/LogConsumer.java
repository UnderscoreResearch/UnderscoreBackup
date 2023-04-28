package com.underscoreresearch.backup.manifest;

import java.io.IOException;

public interface LogConsumer {
    void replayLogEntry(String type, String jsonDefinition) throws IOException;

    String lastSyncedLogFile(String share) throws IOException;

    void setLastSyncedLogFile(String share, String entry) throws IOException;
}
