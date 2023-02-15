package com.underscoreresearch.backup.io;

import java.io.IOException;
import java.util.List;

import com.underscoreresearch.backup.io.implementation.Utils;

public interface IOIndex extends IOProvider {
    List<String> availableKeys(String prefix) throws IOException;

    default boolean rebuildAvailable() throws IOException {
        return Utils.rebuildAvailable(this);
    }

    default List<String> availableLogs(String lastSyncedFile) throws IOException {
        return Utils.getListOfLogFiles(lastSyncedFile, this);
    }
}
