package com.underscoreresearch.backup.file;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Interface for managing a repository of log files.
 * Provides methods to add, reset, and retrieve log files.
 */
public interface LogFileRepository extends Closeable {
    /**
     * Adds a file to the repository.
     *
     * @param file The path to the file to add
     * @throws IOException if an I/O error occurs
     */
    void addFile(String file) throws IOException;

    /**
     * Resets the repository to contain only the specified files.
     *
     * @param files The list of file paths to include in the repository
     * @throws IOException if an I/O error occurs
     */
    void resetFiles(List<String> files) throws IOException;

    /**
     * Gets all files in the repository.
     *
     * @return A list of file paths in the repository
     * @throws IOException if an I/O error occurs
     */
    List<String> getAllFiles() throws IOException;

    /**
     * Gets a random file from the repository.
     *
     * @return The path to a randomly selected file
     * @throws IOException if an I/O error occurs
     */
    String getRandomFile() throws IOException;
}
