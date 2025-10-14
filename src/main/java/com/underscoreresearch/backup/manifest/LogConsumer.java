package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.file.MetadataRepository;

import java.io.IOException;

/**
 * Interface for consuming log entries from the manifest.
 * Provides methods for replaying log entries and tracking synchronization state.
 */
public interface LogConsumer {
    /**
     * Replay a log entry by processing its contents.
     * 
     * @param type The type of log entry
     * @param jsonDefinition The JSON definition of the log entry
     * @throws IOException If there's an error processing the log entry
     */
    void replayLogEntry(String type, String jsonDefinition) throws IOException;

    /**
     * Get the identifier of the last synced log file for a specific share.
     * 
     * @param share The share identifier
     * @return The identifier of the last synced log file
     * @throws IOException If there's an error retrieving the information
     */
    String lastSyncedLogFile(String share) throws IOException;

    /**
     * Set the identifier of the last synced log file for a specific share.
     * 
     * @param share The share identifier
     * @param entry The identifier of the log file
     * @throws IOException If there's an error setting the information
     */
    void setLastSyncedLogFile(String share, String entry) throws IOException;

    /**
     * Set whether the consumer is operating in recovery mode.
     * 
     * @param recoveryMode True if in recovery mode, false otherwise
     */
    void setRecoveryMode(boolean recoveryMode);

    /**
     * Get the metadata repository associated with this log consumer.
     * 
     * @return The metadata repository
     */
    MetadataRepository getMetadataRepository();
}
