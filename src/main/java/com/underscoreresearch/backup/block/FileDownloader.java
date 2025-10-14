package com.underscoreresearch.backup.block;

import com.underscoreresearch.backup.model.BackupFile;

import java.io.IOException;

/**
 * Interface for downloading complete files from backup storage.
 * Handles the process of restoring files from backup blocks.
 */
public interface FileDownloader {
    /**
     * Download a file from backup storage to a local destination.
     * 
     * @param source The backup file to download
     * @param destination The local path where the file should be saved
     * @param password The password to decrypt the file
     * @throws IOException If there's an error downloading or saving the file
     */
    void downloadFile(BackupFile source, String destination, String password) throws IOException;

    /**
     * Shutdown the downloader and release any resources.
     */
    void shutdown();
}
