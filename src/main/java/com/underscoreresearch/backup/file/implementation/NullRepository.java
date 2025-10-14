package com.underscoreresearch.backup.file.implementation;

import com.underscoreresearch.backup.file.CloseableLock;
import com.underscoreresearch.backup.file.CloseableMap;
import com.underscoreresearch.backup.file.CloseableSortedMap;
import com.underscoreresearch.backup.file.CloseableStream;
import com.underscoreresearch.backup.file.LogFileRepository;
import com.underscoreresearch.backup.file.MapSerializer;
import com.underscoreresearch.backup.file.MetadataRepository;
import com.underscoreresearch.backup.file.MetadataRepositoryStorage;
import com.underscoreresearch.backup.file.RepositoryOpenMode;
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
 * Null implementation of MetadataRepository.
 * This implementation does nothing and returns null or empty values for all methods.
 * It is used as a placeholder when no repository is needed.
 */
public class NullRepository implements MetadataRepository {
    /**
     * Gets a dummy lock that does nothing.
     *
     * @return A dummy lock
     */
    private static CloseableLock getDummyLock() {
        return new CloseableLock() {
            @Override
            public void close() {
            }

            @Override
            public boolean requested() {
                return false;
            }
        };
    }

    /**
     * Adds a file to the repository. This implementation does nothing.
     *
     * @param file The file to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addFile(BackupFile file) throws IOException {
    }

    /**
     * Gets the last synced log file for a share. This implementation returns null.
     *
     * @param share The share ID
     * @return null
     */
    @Override
    public String lastSyncedLogFile(String share) {
        return null;
    }

    /**
     * Sets the last synced log file for a share. This implementation does nothing.
     *
     * @param share The share ID
     * @param entry The log file entry
     */
    @Override
    public void setLastSyncedLogFile(String share, String entry) {

    }

    /**
     * Adds an additional block to the repository. This implementation does nothing.
     *
     * @param block The block to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addAdditionalBlock(BackupBlockAdditional block) throws IOException {

    }

    /**
     * Gets an additional block from the repository. This implementation returns null.
     *
     * @param publicKey The public key
     * @param blockHash The block hash
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupBlockAdditional additionalBlock(String publicKey, String blockHash) throws IOException {
        return null;
    }

    /**
     * Acquires an update lock. This implementation returns a dummy lock.
     *
     * @return A dummy lock
     */
    @Override
    public CloseableLock acquireUpdateLock() {
        return getDummyLock();
    }

    /**
     * Deletes an additional block from the repository. This implementation does nothing.
     *
     * @param publicKey The public key
     * @param blockHash The block hash
     */
    @Override
    public void deleteAdditionalBlock(String publicKey, String blockHash) {

    }

    /**
     * Adds an updated file to the repository. This implementation returns false.
     *
     * @param file The file to add
     * @param howOftenMs How often the file should be updated
     * @return false
     */
    @Override
    public boolean addUpdatedFile(BackupUpdatedFile file, long howOftenMs) {
        return false;
    }

    /**
     * Removes an updated file from the repository. This implementation does nothing.
     *
     * @param file The file to remove
     */
    @Override
    public void removeUpdatedFile(BackupUpdatedFile file) {

    }

    /**
     * Gets all updated files from the repository. This implementation returns null.
     *
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupUpdatedFile> getUpdatedFiles() throws IOException {
        return null;
    }

    /**
     * Upgrades the repository storage. This implementation does nothing.
     */
    @Override
    public void upgradeStorage() {
    }

    /**
     * Creates a storage revision. This implementation returns null.
     *
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public MetadataRepositoryStorage createStorageRevision() throws IOException {
        return null;
    }

    /**
     * Cancels a storage revision. This implementation does nothing.
     *
     * @param newStorage The new storage
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void cancelStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {

    }

    /**
     * Installs a storage revision. This implementation does nothing.
     *
     * @param newStorage The new storage
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void installStorageRevision(MetadataRepositoryStorage newStorage) throws IOException {

    }

    /**
     * Gets the configuration hash. This implementation returns null.
     *
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public String getConfigurationHash() throws IOException {
        return null;
    }

    /**
     * Sets the configuration hash. This implementation does nothing.
     *
     * @param hash The hash to set
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void setConfigurationHash(String hash) throws IOException {

    }

    /**
     * Checks if errors are detected in the repository. This implementation returns false.
     *
     * @return false
     */
    @Override
    public boolean isErrorsDetected() {
        return false;
    }

    /**
     * Sets whether errors are detected in the repository. This implementation does nothing.
     *
     * @param errorsDetected Whether errors are detected
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void setErrorsDetected(boolean errorsDetected) throws IOException {

    }

    /**
     * Creates a temporary map. This implementation returns null.
     *
     * @param serializer The serializer to use
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public <K, V> CloseableMap<K, V> temporaryMap(MapSerializer<K, V> serializer) throws IOException {
        return null;
    }

    /**
     * Creates a temporary sorted map. This implementation returns null.
     *
     * @param serializer The serializer to use
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public <K, V> CloseableSortedMap<K, V> temporarySortedMap(MapSerializer<K, V> serializer) throws IOException {
        return null;
    }

    /**
     * Acquires an exclusive lock. This implementation returns null.
     *
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableLock exclusiveLock() throws IOException {
        return null;
    }

    /**
     * Compacts the repository. This implementation does nothing.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void compact() throws IOException {

    }

    /**
     * Gets a file by path. This implementation returns null.
     *
     * @param path The path of the file
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public List<ExternalBackupFile> file(String path) throws IOException {
        return null;
    }

    /**
     * Gets a file by path and timestamp. This implementation returns null.
     *
     * @param path The path of the file
     * @param timestamp The timestamp of the file
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupFile file(String path, Long timestamp) throws IOException {
        return null;
    }

    /**
     * Deletes a file from the repository. This implementation returns false.
     *
     * @param file The file to delete
     * @return false
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deleteFile(BackupFile file) throws IOException {
        return false;
    }

    /**
     * Gets existing file parts by hash. This implementation returns null.
     *
     * @param partHash The hash of the part
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public List<BackupFilePart> existingFilePart(String partHash) throws IOException {
        return null;
    }

    /**
     * Deletes a file part from the repository. This implementation returns false.
     *
     * @param filePart The file part to delete
     * @return false
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deleteFilePart(BackupFilePart filePart) throws IOException {
        return false;
    }

    /**
     * Adds a block to the repository. This implementation does nothing.
     *
     * @param block The block to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addBlock(BackupBlock block) throws IOException {
    }

    /**
     * Gets a block by hash. This implementation returns null.
     *
     * @param hash The hash of the block
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupBlock block(String hash) throws IOException {
        return null;
    }

    /**
     * Deletes a block from the repository. This implementation returns false.
     *
     * @param block The block to delete
     * @return false
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deleteBlock(BackupBlock block) throws IOException {
        return false;
    }

    /**
     * Adds a temporary block to the repository. This implementation does nothing.
     *
     * @param block The block to add
     */
    @Override
    public void addTemporaryBlock(BackupBlock block) {
    }

    /**
     * Installs temporary blocks in the repository. This implementation does nothing.
     */
    @Override
    public void installTemporaryBlocks() {
    }

    /**
     * Adds a directory to the repository. This implementation does nothing.
     *
     * @param directory The directory to add
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addDirectory(BackupDirectory directory) throws IOException {
    }

    /**
     * Gets a directory by path and timestamp. This implementation returns null.
     *
     * @param path The path of the directory
     * @param timestamp The timestamp of the directory
     * @param accumulative Whether to accumulate directories
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupDirectory directory(String path, Long timestamp, boolean accumulative) throws IOException {
        return null;
    }

    /**
     * Deletes a directory from the repository. This implementation returns false.
     *
     * @param path The path of the directory
     * @param timestamp The timestamp of the directory
     * @return false
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deleteDirectory(String path, long timestamp) throws IOException {
        return false;
    }

    /**
     * Pushes an active path to the repository. This implementation does nothing.
     *
     * @param setId The ID of the backup set
     * @param path The path to push
     * @param pendingFiles The pending files
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void pushActivePath(String setId, String path, BackupActivePath pendingFiles) throws IOException {
    }

    /**
     * Checks if a path is active. This implementation returns false.
     *
     * @param setId The ID of the backup set
     * @param path The path to check
     * @return false
     */
    @Override
    public boolean hasActivePath(String setId, String path) {
        return false;
    }

    /**
     * Pops an active path from the repository. This implementation does nothing.
     *
     * @param setId The ID of the backup set
     * @param path The path to pop
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void popActivePath(String setId, String path) throws IOException {
    }

    /**
     * Deletes a partial file from the repository. This implementation returns false.
     *
     * @param file The file to delete
     * @return false
     * @throws IOException if an I/O error occurs
     */
    @Override
    public boolean deletePartialFile(BackupPartialFile file) throws IOException {
        return false;
    }

    /**
     * Saves a partial file to the repository. This implementation does nothing.
     *
     * @param file The file to save
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void savePartialFile(BackupPartialFile file) throws IOException {

    }

    /**
     * Clears all partial files from the repository. This implementation does nothing.
     */
    @Override
    public void clearPartialFiles() {

    }

    /**
     * Gets the log file repository. This implementation returns null.
     *
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public LogFileRepository getLogFileRepository() throws IOException {
        return null;
    }

    /**
     * Gets a partial file from the repository. This implementation returns null.
     *
     * @param file The file to get
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public BackupPartialFile getPartialFile(BackupPartialFile file) throws IOException {
        return null;
    }

    /**
     * Gets all active paths for a backup set. This implementation returns null.
     *
     * @param setId The ID of the backup set
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public TreeMap<String, BackupActivePath> getActivePaths(String setId) throws IOException {
        return null;
    }

    /**
     * Flushes logging to disk. This implementation does nothing.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void flushLogging() throws IOException {
    }

    /**
     * Opens the repository. This implementation does nothing.
     *
     * @param openMode The mode to open the repository in
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void open(RepositoryOpenMode openMode) throws IOException {
    }

    /**
     * Closes the repository. This implementation does nothing.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
    }

    /**
     * Gets all files in the repository. This implementation returns null.
     *
     * @param ascending Whether to sort in ascending order
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupFile> allFiles(boolean ascending) throws IOException {
        return null;
    }

    /**
     * Gets all blocks in the repository. This implementation returns null.
     *
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupBlock> allBlocks() throws IOException {
        return null;
    }

    /**
     * Gets all additional blocks in the repository. This implementation returns null.
     *
     * @return null
     */
    @Override
    public CloseableStream<BackupBlockAdditional> allAdditionalBlocks() {
        return null;
    }

    /**
     * Gets all file parts in the repository. This implementation returns null.
     *
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupFilePart> allFileParts() throws IOException {
        return null;
    }

    /**
     * Gets all directories in the repository. This implementation returns null.
     *
     * @param ascending Whether to sort in ascending order
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public CloseableStream<BackupDirectory> allDirectories(boolean ascending) throws IOException {
        return null;
    }

    /**
     * Adds a pending set to the repository. This implementation does nothing.
     *
     * @param scheduledTime The scheduled time
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void addPendingSets(BackupPendingSet scheduledTime) throws IOException {

    }

    /**
     * Deletes a pending set from the repository. This implementation does nothing.
     *
     * @param setId The ID of the set to delete
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void deletePendingSets(String setId) throws IOException {

    }

    /**
     * Gets all pending sets from the repository. This implementation returns null.
     *
     * @return null
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Set<BackupPendingSet> getPendingSets() throws IOException {
        return null;
    }

    /**
     * Acquires a lock on the repository. This implementation returns a dummy lock.
     *
     * @return A dummy lock
     */
    @Override
    public CloseableLock acquireLock() {
        return getDummyLock();
    }

    /**
     * Gets the number of blocks in the repository. This implementation returns 0.
     *
     * @return 0
     */
    @Override
    public long getBlockCount() {
        return 0;
    }

    /**
     * Gets the number of files in the repository. This implementation returns 0.
     *
     * @return 0
     */
    @Override
    public long getFileCount() {
        return 0;
    }

    /**
     * Gets the number of directories in the repository. This implementation returns 0.
     *
     * @return 0
     */
    @Override
    public long getDirectoryCount() {
        return 0;
    }

    /**
     * Gets the number of parts in the repository. This implementation returns 0.
     *
     * @return 0
     */
    @Override
    public long getPartCount() {
        return 0;
    }

    /**
     * Clears the repository. This implementation does nothing.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void clear() throws IOException {

    }
}
