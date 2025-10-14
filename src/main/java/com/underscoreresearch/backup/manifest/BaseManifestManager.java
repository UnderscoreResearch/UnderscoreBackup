package com.underscoreresearch.backup.manifest;

import java.io.IOException;

/**
 * Base interface for manifest managers, providing core functionality for log management.
 */
public interface BaseManifestManager {
    /**
     * Add a log entry to the manifest.
     * 
     * @param type The type of log entry
     * @param jsonDefinition The JSON definition of the log entry
     */
    void addLogEntry(String type, String jsonDefinition);

    /**
     * Synchronize the log to ensure all entries are persisted.
     * 
     * @throws IOException If there's an error synchronizing the log
     */
    void syncLog() throws IOException;

    /**
     * Initialize the manifest manager with a log consumer.
     * 
     * @param logConsumer The consumer for processing log entries
     * @param immediate Whether to process logs immediately
     * @throws IOException If there's an error during initialization
     */
    void initialize(LogConsumer logConsumer, boolean immediate) throws IOException;

    /**
     * Validate the identity associated with this manifest manager.
     */
    void validateIdentity();

    /**
     * Wait for all pending uploads to complete.
     */
    void waitUploads();

    /**
     * Shutdown the manifest manager and release resources.
     * 
     * @throws IOException If there's an error during shutdown
     */
    void shutdown() throws IOException;
}
