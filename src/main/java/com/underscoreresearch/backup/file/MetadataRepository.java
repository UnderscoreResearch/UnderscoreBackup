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
 * Interface for managing backup metadata repository.
 * Provides methods to store, retrieve, and manage backup metadata including files,
 * blocks, directories, and other backup-related information.
 */
public interface MetadataRepository {
    /**
     * Adds a file to the repository.
     *
     * @param file The backup file to add
     * @throws IOException if an I/O error occurs
     */
    void addFile(BackupFile file) throws IOException;

    /**
     * Retrieves external backup files for a path.
     *
     * @param path The path to look up
     * @return A list of external backup files for the path
     * @throws IOException if an I/O error occurs
     */
    List<ExternalBackupFile> file(String path) throws IOException;

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
     * Deletes a file from the repository.
     *
     * @param file The backup file to delete
     * @return true if the file was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deleteFile(BackupFile file) throws IOException;

    /**
     * Finds existing file parts with a specific hash.
     *
     * @param partHash The hash of the file part to find
     * @return A list of file parts with the specified hash
     * @throws IOException if an I/O error occurs
     */
    List<BackupFilePart> existingFilePart(String partHash) throws IOException;

    /**
     * Deletes a file part from the repository.
     *
     * @param filePart The file part to delete
     * @return true if the file part was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deleteFilePart(BackupFilePart filePart) throws IOException;

    /**
     * Adds a block to the repository.
     *
     * @param block The backup block to add
     * @throws IOException if an I/O error occurs
     */
    void addBlock(BackupBlock block) throws IOException;

    /**
     * Retrieves a block by its hash.
     *
     * @param hash The hash of the block to retrieve
     * @return The backup block, or null if not found
     * @throws IOException if an I/O error occurs
     */
    BackupBlock block(String hash) throws IOException;

    /**
     * Deletes a block from the repository.
     *
     * @param block The backup block to delete
     * @return true if the block was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deleteBlock(BackupBlock block) throws IOException;

    /**
     * Adds a temporary block to the repository.
     *
     * @param block The backup block to add temporarily
     * @throws IOException if an I/O error occurs
     */
    void addTemporaryBlock(BackupBlock block) throws IOException;

    /**
     * Installs temporary blocks into the permanent repository.
     *
     * @throws IOException if an I/O error occurs
     */
    void installTemporaryBlocks() throws IOException;

    /**
     * Adds a directory to the repository.
     *
     * @param directory The backup directory to add
     * @throws IOException if an I/O error occurs
     */
    void addDirectory(BackupDirectory directory) throws IOException;

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
     * Deletes a directory from the repository.
     *
     * @param path The path of the directory to delete
     * @param timestamp The timestamp of the directory version to delete
     * @return true if the directory was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deleteDirectory(String path, long timestamp) throws IOException;

    /**
     * Pushes an active path to the repository.
     *
     * @param setId The ID of the backup set
     * @param path The path to push
     * @param pendingFiles The active path information
     * @throws IOException if an I/O error occurs
     */
    void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException;

    /**
     * Checks if an active path exists in the repository.
     *
     * @param setId The ID of the backup set
     * @param path The path to check
     * @return true if the active path exists, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean hasActivePath(String setId, String path) throws IOException;

    /**
     * Removes an active path from the repository.
     *
     * @param setId The ID of the backup set
     * @param path The path to remove
     * @throws IOException if an I/O error occurs
     */
    void popActivePath(String setId, String path) throws IOException;

    /**
     * Deletes a partial file from the repository.
     *
     * @param file The partial file to delete
     * @return true if the partial file was deleted, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean deletePartialFile(BackupPartialFile file) throws IOException;

    /**
     * Saves a partial file to the repository.
     *
     * @param file The partial file to save
     * @throws IOException if an I/O error occurs
     */
    void savePartialFile(BackupPartialFile file) throws IOException;

    /**
     * Clears all partial files from the repository.
     *
     * @throws IOException if an I/O error occurs
     */
    void clearPartialFiles() throws IOException;

    /**
     * Gets the log file repository.
     *
     * @return The log file repository
     * @throws IOException if an I/O error occurs
     */
    LogFileRepository getLogFileRepository() throws IOException;

    /**
     * Retrieves a partial file from the repository.
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
     * Flushes logging data to persistent storage.
     *
     * @throws IOException if an I/O error occurs
     */
    void flushLogging() throws IOException;

    /**
     * Opens the repository in the specified mode.
     *
     * @param openMode The mode to open the repository in
     * @throws IOException if an I/O error occurs
     */
    void open(RepositoryOpenMode openMode) throws IOException;

    /**
     * Closes the repository.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;

    /**
     * Gets a stream of all files in the repository.
     *
     * @param ascending Whether to return files in ascending order
     * @return A closeable stream of backup files
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException;

    /**
     * Gets a stream of all blocks in the repository.
     *
     * @return A closeable stream of backup blocks
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupBlock> allBlocks() throws IOException;

    /**
     * Gets a stream of all additional blocks in the repository.
     *
     * @return A closeable stream of additional backup blocks
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupBlockAdditional> allAdditionalBlocks() throws IOException;

    /**
     * Gets a stream of all file parts in the repository.
     *
     * @return A closeable stream of backup file parts
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupFilePart> allFileParts() throws IOException;

    /**
     * Gets a stream of all directories in the repository.
     *
     * @param ascending Whether to return directories in ascending order
     * @return A closeable stream of backup directories
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException;

    /**
     * Adds pending sets to the repository.
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
     * Gets all pending sets in the repository.
     *
     * @return A set of pending backup sets
     * @throws IOException if an I/O error occurs
     */
    Set<BackupPendingSet> getPendingSets() throws IOException;

    /**
     * Acquires a lock on the repository.
     *
     * @return A closeable lock
     */
    CloseableLock acquireLock();

    /**
     * Gets the count of blocks in the repository.
     *
     * @return The number of blocks
     * @throws IOException if an I/O error occurs
     */
    long getBlockCount() throws IOException;

    /**
     * Gets the count of files in the repository.
     *
     * @return The number of files
     * @throws IOException if an I/O error occurs
     */
    long getFileCount() throws IOException;

    /**
     * Gets the count of directories in the repository.
     *
     * @return The number of directories
     * @throws IOException if an I/O error occurs
     */
    long getDirectoryCount() throws IOException;

    /**
     * Gets the count of file parts in the repository.
     *
     * @return The number of file parts
     * @throws IOException if an I/O error occurs
     */
    long getPartCount() throws IOException;

    /**
     * Clears all data from the repository.
     *
     * @throws IOException if an I/O error occurs
     */
    void clear() throws IOException;

    /**
     * Gets the last synced log file for a share.
     *
     * @param share The share identifier
     * @return The path of the last synced log file
     * @throws IOException if an I/O error occurs
     */
    String lastSyncedLogFile(String share) throws IOException;

    /**
     * Sets the last synced log file for a share.
     *
     * @param share The share identifier
     * @param entry The path of the log file
     * @throws IOException if an I/O error occurs
     */
    void setLastSyncedLogFile(String share, String entry) throws IOException;

    /**
     * Adds an additional block to the repository.
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
     * Acquires an update lock on the repository.
     *
     * @return A closeable lock
     */
    CloseableLock acquireUpdateLock();

    /**
     * Deletes an additional block from the repository.
     *
     * @param publicKey The public key associated with the block
     * @param blockHash The hash of the block
     * @throws IOException if an I/O error occurs
     */
    void deleteAdditionalBlock(String publicKey, String blockHash) throws IOException;

    /**
     * Adds an updated file to the repository.
     *
     * @param file The updated file to add
     * @param howOftenMs How often the file should be checked for updates (in milliseconds)
     * @return true if the file was added, false otherwise
     * @throws IOException if an I/O error occurs
     */
    boolean addUpdatedFile(BackupUpdatedFile file, long howOftenMs) throws IOException;

    /**
     * Removes an updated file from the repository.
     *
     * @param file The updated file to remove
     * @throws IOException if an I/O error occurs
     */
    void removeUpdatedFile(BackupUpdatedFile file) throws IOException;

    /**
     * Gets a stream of all updated files in the repository.
     *
     * @return A closeable stream of updated files
     * @throws IOException if an I/O error occurs
     */
    CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException;

    /**
     * Upgrades the repository storage to the latest version.
     *
     * @throws IOException if an I/O error occurs
     */
    void upgradeStorage() throws IOException;

    /**
     * Creates a new storage revision for the repository.
     *
     * @return The new storage revision
     * @throws IOException if an I/O error occurs
     */
    MetadataRepositoryStorage createStorageRevision() throws IOException;

    /**
     * Cancels a storage revision.
     *
     * @param newStorage The storage revision to cancel
     * @throws IOException if an I/O error occurs
     */
    void cancelStorageRevision(MetadataRepositoryStorage newStorage) throws IOException;

    /**
     * Installs a storage revision.
     *
     * @param newStorage The storage revision to install
     * @throws IOException if an I/O error occurs
     */
    void installStorageRevision(MetadataRepositoryStorage newStorage) throws IOException;

    /**
     * Gets the configuration hash for the repository.
     *
     * @return The configuration hash
     * @throws IOException if an I/O error occurs
     */
    String getConfigurationHash() throws IOException;

    /**
     * Sets the configuration hash for the repository.
     *
     * @param hash The configuration hash to set
     * @throws IOException if an I/O error occurs
     */
    void setConfigurationHash(String hash) throws IOException;

    /**
     * Checks if errors have been detected in the repository.
     *
     * @return true if errors have been detected, false otherwise
     */
    boolean isErrorsDetected();

    /**
     * Sets whether errors have been detected in the repository.
     *
     * @param errorsDetected Whether errors have been detected
     * @throws IOException if an I/O error occurs
     */
    void setErrorsDetected(boolean errorsDetected) throws IOException;

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
     * Acquires an exclusive lock on the repository.
     * All changes must happen on the granting thread.
     *
     * @return A closeable lock
     * @throws IOException if an I/O error occurs
     */
    CloseableLock exclusiveLock() throws IOException;

    /**
     * Compacts the repository to reclaim space and improve performance.
     *
     * @throws IOException if an I/O error occurs
     */
    void compact() throws IOException;
}
