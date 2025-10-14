package com.underscoreresearch.backup.file;

import com.underscoreresearch.backup.model.BackupFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Interface for accessing the file system.
 * Provides methods to read, write, and manage files and directories.
 */
public interface FileSystemAccess {
    /**
     * Gets the files in a directory.
     *
     * @param path The path to the directory
     * @return A set of BackupFile objects representing the files in the directory
     */
    Set<BackupFile> directoryFiles(String path);

    /**
     * Applies permissions to a file or directory.
     *
     * @param path The file or directory to modify
     * @param permissions The permissions to apply
     * @throws IOException if an I/O error occurs
     */
    void applyPermissions(File path, String permissions) throws IOException;

    /**
     * Reads data from a file.
     *
     * @param path The path to the file
     * @param buffer The buffer to read into
     * @param offset The offset in the file to start reading from
     * @param length The number of bytes to read
     * @return The number of bytes read
     * @throws IOException if an I/O error occurs
     */
    int readData(String path, byte[] buffer, long offset, int length) throws IOException;

    /**
     * Writes data to a file.
     *
     * @param path The path to the file
     * @param buffer The buffer containing the data to write
     * @param offset The offset in the file to start writing at
     * @param length The number of bytes to write
     * @throws IOException if an I/O error occurs
     */
    void writeData(String path, byte[] buffer, long offset, int length) throws IOException;

    /**
     * Completes a file after writing.
     * This may involve setting file attributes, permissions, or other finalization steps.
     *
     * @param file The backup file metadata
     * @param path The path to the file
     * @param length The final length of the file
     * @throws IOException if an I/O error occurs
     */
    void completeFile(BackupFile file, String path, long length) throws IOException;

    /**
     * Extracts permissions from a file or directory.
     *
     * @param path The path to the file or directory
     * @return A string representation of the permissions
     * @throws IOException if an I/O error occurs
     */
    String extractPermissions(String path) throws IOException;

    /**
     * Deletes a file or directory.
     *
     * @param path The path to the file or directory to delete
     * @throws IOException if an I/O error occurs
     */
    void delete(String path) throws IOException;
}
