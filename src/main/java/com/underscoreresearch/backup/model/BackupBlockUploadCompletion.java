package com.underscoreresearch.backup.model;

public interface BackupBlockUploadCompletion {
    void completed(BackupBlock updatedBlock, boolean success);
}
