package com.underscoreresearch.backup.manifest;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.model.BackupFile;

public interface BackupSearchAccess {
    CloseableLock acquireLock();

    Stream<BackupFile> searchFiles(Pattern pathPattern) throws IOException;
}