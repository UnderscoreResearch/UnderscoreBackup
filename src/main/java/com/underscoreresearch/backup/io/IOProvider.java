package com.underscoreresearch.backup.io;

import java.io.IOException;

/**
 * Interface for IO providers that handle storage operations.
 * Defines methods for uploading, downloading, and managing data in storage.
 */
public interface IOProvider {
    /**
     * Upload data to storage with a suggested key.
     *
     * @param suggestedKey The suggested key for the data
     * @param data The data to upload
     * @return The actual key used for the uploaded data
     * @throws IOException If there's an error uploading the data
     */
    String upload(String suggestedKey, byte[] data) throws IOException;

    /**
     * Download data from storage using a key.
     *
     * @param key The key for the data to download
     * @return The downloaded data
     * @throws IOException If there's an error downloading the data
     */
    byte[] download(String key) throws IOException;

    /**
     * Get a unique cache key for this provider.
     * This key is used to identify cached data for this provider.
     *
     * @return The cache key
     */
    String getCacheKey();

    /**
     * Check if data exists at the specified key.
     *
     * @param key The key to check
     * @return True if data exists at the key, false otherwise
     * @throws IOException If there's an error checking for existence
     */
    boolean exists(String key) throws IOException;

    /**
     * Delete data at the specified key.
     *
     * @param key The key for the data to delete
     * @throws IOException If there's an error deleting the data
     */
    void delete(String key) throws IOException;

    /**
     * Check if the provider credentials are valid.
     *
     * @param readonly Whether to check for read-only access
     * @throws IOException If the credentials are invalid or there's an error checking
     */
    void checkCredentials(boolean readonly) throws IOException;
}
