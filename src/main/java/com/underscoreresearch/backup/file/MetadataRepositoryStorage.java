package com.underscoreresearch.backup.file;

import com.underscoreresearch.backup.manifest.model.BackupDirectory;
import com.underscoreresearch.backup.model.BackupActivePath;
import com.underscoreresearch.backup.model.BackupBlock;
import com.underscoreresearch.backup.model.BackupBlockAdditional;
import com.underscoreresearch.backup.model.BackupFile;
import com.underscoreresearch.backup.model.BackupFilePart;
import com.underscoreresearch.backup.model.BackupPartialFile;
import com.underscoreresearch.backup.model.BackupPendingSet;
import com.underscoreresearch.backup.model.BackupUpdatedFile;
import com.underscoreresearch.backup.model.ExternalBackupFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Interface for storage implementation of the metadata repository.
 * Provides low-level storage operations for backup metadata.
 */
public interface MetadataRepositoryStorage {
    /**
     * Opens the storage in the specified mode.
     *
     * @param openMode The mode to open the storage in
     * @throws IOException if an I/O error occurs
     */
    void open(RepositoryOpenMode openMode) throws IOException;

    /**
     * Closes the storage.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;

    /**
     * Retrieves external backup files for a path.
     *
     * @param path The path to look up
     * @return A list of external backup files for the path
     * @throws IOException if an I/O error occurs
     */
    List<ExternalBackupFile> file(String path) throws IOException;

    /**
     * Gets a stream of all files in the storage.
     *
     * @param ascending Whether to return files in ascending order
     * @return A closeable stream of backup files
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException;

    /**
     * Gets a stream of all blocks in the storage.
     *
     * @return A closeable stream of backup blocks
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupBlock> allBlocks() throws IOException;

    /**
     * Finds existing file parts with a specific hash.
     *
     * @param partHash The hash of the file part to find
     * @return A list of file parts with the specified hash
     * @throws IOException if an I/O error occurs
     */
    List<BackupFilePart> existingFilePart(String partHash) throws IOException;

    /**
     * Gets a stream of all file parts in the storage.
     *
     * @return A closeable stream of backup file parts
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupFilePart> allFileParts() throws IOException;

    /**
     * Gets a stream of all directories in the storage.
     *
     * @param ascending Whether to return directories in ascending order
     * @return A closeable stream of backup directories
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException;

    /**
     * Gets a stream of all additional blocks in the storage.
     *
     * @return A closeable stream of additional backup blocks
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupBlockAdditional> allAdditionalBlocks() throws IOException;

    /**
     * Adds pending sets to the storage.
     *
     * @param scheduledTime The pending set to add
     * @throws IOException if an I/O error occurs
     */
    void addPendingSets(BackupPendingSet scheduledTime) throws IOException;

    /**
     * Deletes pending sets for a backup set.
     *
     * @param setId The ID of the backup set
     * @throws IOException if an I/O error occurs
     */
    void deletePendingSets(String setId) throws IOException;

    /**
     * Gets all pending sets in the storage.
     *
     * @return A set of pending backup sets
     * @throws IOException if an I/O error occurs
     */
    Set<BackupPendingSet> getPendingSets() throws IOException;

    /**
     * Retrieves a backup file for a path and timestamp.
     *
     * @param path The path to look up
     * @param timestamp The timestamp of the file version to retrieve
     * @return The backup file, or null if not found
     * @throws IOException if an I/O error occurs
     */
    BackupFile file(String path, Long timestamp) throws IOException;

    /**
     * Retrieves a block by its hash.
     *
     * @param hash The hash of the block to retrieve
     * @return The backup block, or null if not found
     * @throws IOException if an I/O error occurs
     */
    BackupBlock block(String hash) throws IOException;

    /**
     * Retrieves a directory by path, timestamp, and accumulation preference.
     *
     * @param path The path of the directory to retrieve
     * @param timestamp The timestamp of the directory version to retrieve
     * @param accumulative Whether to accumulate directory entries from previous versions
     * @return The backup directory, or null if not found
     * @throws IOException if an I/O error occurs
     */
    BackupDirectory directory(String path, Long timestamp, boolean accumulative) throws IOException;

    /**
     * Adds a file to the storage.
     *
     * @param file The backup file to add
     * @throws IOException if an I/O error occurs
     */
    void addFile(BackupFile file) throws IOException;

    /**
     * Adds a file part to the storage.
     *
     * @param part The file part to add
     * @throws IOException if an I/O error occurs
     */
    void addFilePart(BackupFilePart part) throws IOException;

    /**
     * Adds a block to the storage.
     *
     * @param block The backup block to add
     * @throws IOException if an I/O error occurs
     */
    void addBlock(BackupBlock block) throws IOException;

    /**
     * Adds a temporary block to the storage.
     *
     * @param block The backup block to add temporarily
     * @throws IOException if an I/O error occurs
     */
    void addTemporaryBlock(BackupBlock block) throws IOException;

    /**
     * Switches the blocks table to use temporary blocks as the main blocks.
     *
     * @throws IOException if an I/O error occurs
     */
    void switchBlocksTable() throws IOException;

    /**
     * Adds a directory to the storage.
     *
     * @param directory The backup directory to add
     * @throws IOException if an I/O error occurs
     */
    void addDirectory(BackupDirectory directory) throws IOException;

    /**
     * Deletes a block from the storage.
     *
     * @param block The backup block to delete
     * @return true if the block was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deleteBlock(BackupBlock block) throws IOException;

    /**
     * Deletes a file from the storage.
     *
     * @param file The backup file to delete
     * @return true if the file was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deleteFile(BackupFile file) throws IOException;

    /**
     * Deletes a file part from the storage.
     *
     * @param part The file part to delete
     * @return true if the file part was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deleteFilePart(BackupFilePart part) throws IOException;

    /**
     * Deletes a directory from the storage.
     *
     * @param path The path of the directory to delete
     * @param timestamp The timestamp of the directory version to delete
     * @return true if the directory was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deleteDirectory(String path, long timestamp) throws IOException;

    /**
     * Pushes an active path to the storage.
     *
     * @param setId The ID of the backup set
     * @param path The path to push
     * @param pendingFiles The active path information
     * @throws IOException if an I/O error occurs
     */
    void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException;

    /**
     * Checks if an active path exists in the storage.
     *
     * @param setId The ID of the backup set
     * @param path The path to check
     * @return true if the active path exists, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean hasActivePath(String setId, String path) throws IOException;

    /**
     * Removes an active path from the storage.
     *
     * @param setId The ID of the backup set
     * @param path The path to remove
     * @throws IOException if an I/O error occurs
     */
    void popActivePath(String setId, String path) throws IOException;

    /**
     * Deletes a partial file from the storage.
     *
     * @param file The partial file to delete
     * @return true if the partial file was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deletePartialFile(BackupPartialFile file) throws IOException;

    /**
     * Saves a partial file to the storage.
     *
     * @param file The partial file to save
     * @throws IOException if an I/O error occurs
     */
    void savePartialFile(BackupPartialFile file) throws IOException;

    /**
     * Clears all partial files from the storage.
     *
     * @throws IOException if an I/O error occurs
     */
    void clearPartialFiles() throws IOException;

    /**
     * Retrieves a partial file from the storage.
     *
     * @param file The partial file to retrieve
     * @return The retrieved partial file, or null if not found
     * @throws IOException if an I/O error occurs
     */
    BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException;

    /**
     * Gets all active paths for a backup set.
     *
     * @param setId The ID of the backup set
     * @return A map of paths to active path information
     * @throws IOException if an I/O error occurs
     */
    TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException;

    /**
     * Gets the count of blocks in the storage.
     *
     * @return The number of blocks
     * @throws IOException if an I/O error occurs
     */
    long getBlockCount() throws IOException;

    /**
     * Gets the count of files in the storage.
     *
     * @return The number of files
     * @throws IOException if an I/O error occurs
     */
    long getFileCount() throws IOException;

    /**
     * Gets the count of directories in the storage.
     *
     * @return The number of directories
     * @throws IOException if an I/O error occurs
     */
    long getDirectoryCount() throws IOException;

    /**
     * Gets the count of file parts in the storage.
     *
     * @return The number of file parts
     * @throws IOException if an I/O error occurs
     */
    long getPartCount() throws IOException;

    /**
     * Gets the count of additional blocks in the storage.
     *
     * @return The number of additional blocks
     * @throws IOException if an I/O error occurs
     */
    long getAdditionalBlockCount() throws IOException;

    /**
     * Gets the count of updated files in the storage.
     *
     * @return The number of updated files
     * @throws IOException if an I/O error occurs
     */
    long getUpdatedFileCount() throws IOException;

    /**
     * Adds an additional block to the storage.
     *
     * @param block The additional block to add
     * @throws IOException if an I/O error occurs
     */
    void addAdditionalBlock(BackupBlockAdditional block) throws IOException;

    /**
     * Retrieves an additional block by public key and block hash.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block
     * @return The additional block, or null if not found
     * @throws IOException if an I/O error occurs
     */
    BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException;

    /**
     * Deletes an additional block from the storage.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block
     * @throws IOException if an I/O error occurs
     */
    void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException;

    /**
     * Adds an updated file to the storage.
     *
     * @param file The updated file to add
     * @param howOften How often the file should be checked for updates (in milliseconds)
     * @return true if the file was added, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean addUpdatedFile(BackupUpdatedFile file, long howOften) throws IOException;

    /**
     * Removes an updated file from the storage.
     *
     * @param file The updated file to remove
     * @throws IOException if an I/O error occurs
     */
    void removeUpdatedFile(BackupUpdatedFile file) throws IOException;

    /**
     * Gets a stream of all updated files in the storage.
     *
     * @return A closeable stream of updated files
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException;

    /**
     * Clears all data from the storage.
     *
     * @throws IOException if an I/O error occurs
     */
    void clear() throws IOException;

    /**
     * Commits changes to the storage.
     *
     * @throws IOException if an I/O error occurs
     */
    void commit() throws IOException;

    /**
     * Checks if the storage needs periodic commits.
     *
     * @return true if periodic commits are needed, false otherwise
     */
    boolean needPeriodicCommits();

    /**
     * Creates a temporary map with the specified serializer.
     *
     * @param serializer The serializer to use for keys and values
     * @param <K> The type of keys
     * @param <V> The type of values
     * @return A closeable map
     * @throws IOException if an I/O error occurs
     */
    <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException;

    /**
     * Creates a temporary sorted map with the specified serializer.
     *
     * @param serializer The serializer to use for keys and values
     * @param <K> The type of keys
     * @param <V> The type of values
     * @return A closeable sorted map
     * @throws IOException if an I/O error occurs
     */
    <K, V> CloseableSortedMap<K, V> temporarySortedMap(MapSerializer<K, V> serializer) throws IOException;

    /**
     * Checks if the storage needs an exclusive lock for commits.
     *
     * @return true if an exclusive lock is needed, false otherwise
     */
    boolean needExclusiveCommitLock();

    /**
     * Acquires an exclusive lock on the storage.
     *
     * @return A closeable lock
     * @throws IOException if an I/O error occurs
     */
    CloseableLock exclusiveLock() throws IOException;
}
