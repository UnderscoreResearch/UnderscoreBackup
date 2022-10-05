package com.underscoreresearch.backup.manifest;

import java.io.IOException;

public interface LogConsumer {
    void replayLogEntry(String type, String jsonDefinition) throws IOException;

    String lastSyncedLogFile() throws IOException;

    void setLastSyncedLogFile(String entry) throws IOException;
}
