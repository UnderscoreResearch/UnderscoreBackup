package com.underscoreresearch.backup.manifest;

import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.model.BackupFile;

import java.io.IOException;
import java.util.regex.Pattern;

public interface BackupSearchAccess {
    CloseableLock acquireLock();

    CloseableStream<BackupFile> searchFiles(Pattern pathPattern, CloseableLock interruptableLock) throws IOException;
}