package com.underscoreresearch.backup.io;

import com.underscoreresearch.backup.model.BackupDestination;
import com.underscoreresearch.backup.model.BackupUploadCompletion;

/**
 * Interface for scheduling uploads to backup destinations.
 * Provides methods for asynchronous upload operations.
 */
public interface UploadScheduler {
    /**
     * Schedule an upload with specific parameters.
     *
     * @param destination The destination to upload to
     * @param key The base key for the upload
     * @param index The index for the upload
     * @param disambiguator The disambiguator for the upload
     * @param data The data to upload
     * @param completionPromise The promise to complete when the upload finishes
     */
    void scheduleUpload(BackupDestination destination, String key, int index, int disambiguator, byte[] data,
                        BackupUploadCompletion completionPromise);

    /**
     * Shutdown the scheduler and release resources.
     */
    void shutdown();

    /**
     * Generate a suggested key based on parameters.
     *
     * @param hash The hash of the data
     * @param index The index for the key
     * @param disambiguator The disambiguator for the key
     * @return The suggested key
     */
    String suggestedKey(String hash, int index, int disambiguator);

    /**
     * Schedule an upload with a suggested path.
     *
     * @param destination The destination to upload to
     * @param suggestedPath The suggested path for the upload
     * @param data The data to upload
     * @param completionPromise The promise to complete when the upload finishes
     */
    void scheduleUpload(BackupDestination destination, String suggestedPath, byte[] data,
                        BackupUploadCompletion completionPromise);

    /**
     * Wait for all scheduled uploads to complete.
     */
    void waitForCompletion();
}
