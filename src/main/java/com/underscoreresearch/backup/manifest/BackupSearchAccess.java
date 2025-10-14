package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.model.BackupFile;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Interface for searching backup contents, providing methods to search files by pattern.
 */
public interface BackupSearchAccess {
    /**
     * Acquire a lock for performing search operations.
     * 
     * @return A closeable lock that should be released after search operations
     */
    CloseableLock acquireLock();

    /**
     * Search for files matching a path pattern.
     * 
     * @param pathPattern The pattern to match file paths against
     * @param interruptableLock A lock that can be used to interrupt the search
     * @return A closeable stream of backup files matching the pattern
     * @throws IOException If there's an error during the search
     */
    CloseableStream<BackupFile> searchFiles(Pattern pathPattern, CloseableLock interruptableLock) throws IOException;
}
