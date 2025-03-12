package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.io.implementation.Utils;

import java.io.IOException;
import java.util.List;

public interface IOIndex extends IOProvider {
    List<String> availableKeys(String prefix) throws IOException;

    default boolean rebuildAvailable() throws IOException {
        return Utils.rebuildAvailable(this);
    }

    default List<String> availableLogs(String lastSyncedFile, boolean all) throws IOException {
        return Utils.getListOfLogFiles(lastSyncedFile, this, all);
    }

    default boolean hasConsistentWrites() {
        return false;
    }
}
