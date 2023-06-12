package com.underscoreresearch.backup.manifest;

import java.io.IOException;
import java.util.regex.Pattern;

import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.model.BackupFile;

public interface BackupSearchAccess {
    CloseableLock acquireLock();

    CloseableStream<BackupFile> searchFiles(Pattern pathPattern, CloseableLock interruptableLock) throws IOException;
}