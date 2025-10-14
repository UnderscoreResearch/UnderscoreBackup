package com.underscoreresearch.backup.model;

/**
 * Interface for handling the completion of backup block uploads.
 * Provides a callback mechanism for when block uploads are completed.
 */

public interface BackupBlockUploadCompletion {
    /**
     * Called when a block upload is completed.
     * 
     * @param updatedBlock The updated block after the upload
     * @param success Whether the upload was successful
     */
    void completed(BackupBlock updatedBlock, boolean success);
}
