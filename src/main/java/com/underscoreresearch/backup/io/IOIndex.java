package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.io.implementation.Utils;

import java.io.IOException;
import java.util.List;

/**
 * Interface for IO providers that support listing available keys.
 * Extends IOProvider with additional methods for key enumeration.
 */
public interface IOIndex extends IOProvider {
    /**
     * List all available keys with the specified prefix.
     *
     * @param prefix The prefix to filter keys by
     * @return List of keys matching the prefix
     * @throws IOException If there's an error accessing the storage
     */
    List<String> availableKeys(String prefix) throws IOException;

    /**
     * Check if the repository can be rebuilt from the available keys.
     *
     * @return True if rebuild is available, false otherwise
     * @throws IOException If there's an error checking rebuild availability
     */
    default boolean rebuildAvailable() throws IOException {
        return Utils.rebuildAvailable(this);
    }

    /**
     * Get a list of available log files after the specified log file.
     *
     * @param lastSyncedFile The last synced log file, or null for all
     * @param all Whether to include all log files
     * @return List of available log files
     * @throws IOException If there's an error accessing the log files
     */
    default List<String> availableLogs(String lastSyncedFile, boolean all) throws IOException {
        return Utils.getListOfLogFiles(lastSyncedFile, this, all);
    }

    /**
     * Check if the provider guarantees consistent writes.
     * A consistent write means that once a write operation completes,
     * the data is guaranteed to be durably stored.
     *
     * @return True if writes are consistent, false otherwise
     */
    default boolean hasConsistentWrites() {
        return false;
    }
}
