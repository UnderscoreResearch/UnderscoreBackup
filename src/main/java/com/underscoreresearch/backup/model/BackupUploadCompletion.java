package com.underscoreresearch.backup.model;

/**
 * Interface for handling the completion of backup uploads.
 * Provides a callback mechanism for when uploads are completed.
 */

public interface BackupUploadCompletion {
    /**
     * Called when an upload is completed.
     * 
     * @param key The key of the uploaded object
     */
    void completed(String key);
}
